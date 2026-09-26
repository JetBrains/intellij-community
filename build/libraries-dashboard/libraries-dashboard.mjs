#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Dashboard of Maven library versions pinned in intellij.libraries.* wrapper modules and .idea/libraries project libraries.
// Report: bun community/build/libraries-dashboard/libraries-dashboard.mjs [--no-cache|--refresh] [--format=html|text|json|all] [--open]
// Bump:   bun community/build/libraries-dashboard/libraries-dashboard.mjs bump <groupId:artifactId>[=<version>]... [--no-cache|--refresh]
// Check:  bun community/build/libraries-dashboard/libraries-dashboard.mjs check [<groupId:artifactId>...]
// The bump also rewrites the version copies listed in VERSION_MIRRORS and prints the project structure test that checks them.
// The bump and the check compare the artifact list of a multi-artifact library with the direct dependencies of its POM.

import {createHash} from "node:crypto"
import {mkdir, readdir, readFile, writeFile} from "node:fs/promises"
import {existsSync} from "node:fs"
import {homedir} from "node:os"
import {basename, dirname, join, resolve} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

const SCRIPT_PATH = fileURLToPath(import.meta.url)
const SELF_DIR = dirname(SCRIPT_PATH)
export const REPO_ROOT = resolve(SELF_DIR, "..", "..", "..")
// Every IDE project that owns a modules.xml, a libraries directory and a jarRepositories.xml.
const PROJECT_ROOTS = [REPO_ROOT, join(REPO_ROOT, "community")]
const OUT_DIR = join(REPO_ROOT, "out/libraries-dashboard")

/**
 * The commands to run after a bump. The Fleet generator copies the JPS library versions into
 * `fleet/build/gradle/jps.versions.toml`, `fleet/build/jps-library-mappings.tsv` and both `fleet/kmp.MODULE.bazel`
 * files, and its `check` mode fails on drift. The `kmp` module extension records the artifact list of each
 * `kmp.MODULE.bazel` in the `MODULE.bazel.lock` of its module, and CI runs Bazel with `--lockfile_mode=error`,
 * so both lockfiles need an update after the dump. The generator exists only in the monorepo checkout.
 */
export function followUpCommands(repoRoot = REPO_ROOT) {
  const commands = ["./build/jpsModelToBazel.cmd"]
  if (existsSync(join(repoRoot, "fleet/build/generateProjectModel.cmd"))) {
    commands.push(
      "./fleet/build/generateProjectModel.cmd dump",
      "./bazel.cmd mod deps --lockfile_mode=update",
      "(cd community && ./bazel.cmd mod deps --lockfile_mode=update)",
    )
  }
  commands.push("bazel run //:format.check")
  return commands
}
const CACHE_PATH = join(OUT_DIR, "cache.json")
const HTML_OUT = join(OUT_DIR, "dashboard.html")
const JSON_OUT = join(OUT_DIR, "dashboard.json")
const CACHE_VERSION = 2
const CACHE_TTL_MS = 24 * 60 * 60 * 1000
const FETCH_CONCURRENCY = 8
const FETCH_TIMEOUT_MS = 15_000
export const MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
const WRAPPER_PREFIX = "intellij.libraries."

export function parseArgs(argv) {
  const opts = { cache: true, refresh: false, format: "all", open: false, command: "report", coordinates: [], kind: null }
  const args = argv.slice(2)
  if (args[0] === "bump" || args[0] === "check") {
    opts.command = args[0]
    args.shift()
  }
  const takesCoordinates = opts.command === "bump" || opts.command === "check"
  for (const a of args) {
    if (a === "--no-cache") opts.cache = false
    else if (a === "--refresh") opts.refresh = true
    else if (a === "--open") opts.open = true
    else if (a.startsWith("--format=")) opts.format = a.slice("--format=".length)
    else if (a.startsWith("--kind=") && takesCoordinates) opts.kind = a.slice("--kind=".length)
    else if (a === "-h" || a === "--help") {
      console.log(
        "Usage: bun libraries-dashboard.mjs [--no-cache] [--refresh] [--format=html|text|json|all] [--open]\n" +
          "       bun libraries-dashboard.mjs bump <groupId:artifactId>[=<version>]... [--kind=wrapper|project] [--no-cache] [--refresh]\n" +
          "       bun libraries-dashboard.mjs check [<groupId:artifactId>...] [--kind=wrapper|project] [--no-cache] [--refresh]\n" +
          "  --no-cache  ignore cached Maven/POM responses\n" +
          "  --refresh   rewrite cache from scratch\n" +
          "  --format    output mode (default: all = html + terminal)\n" +
          "  --open      open the HTML report in the default browser\n" +
          "  bump        rewrite maven-id, jar URLs and sha256 of the named libraries (default target: resolved latest);\n" +
          "              also rewrite the version copies outside the JPS model (jps-bootstrap pom.xml, annotations quick fix)\n" +
          "              and check the artifact list of a multi-artifact library against the direct dependencies of the new POM\n" +
          "  check       compare the artifact list of every multi-artifact library, or of the named ones, with the direct\n" +
          "              dependencies of its POM; JPS fails on a mismatch that Bazel accepts\n" +
          "  --kind      bump or check only wrapper modules or only .idea/libraries project libraries (default: both)"
      )
      process.exit(0)
    } else if (takesCoordinates && !a.startsWith("-")) {
      opts.coordinates.push(a)
    } else {
      console.error(`Unknown arg: ${a}`)
      process.exit(2)
    }
  }
  if (!["html", "text", "json", "all"].includes(opts.format)) {
    console.error(`Bad --format: ${opts.format}`)
    process.exit(2)
  }
  if (opts.command === "bump" && opts.coordinates.length === 0) {
    console.error("bump needs at least one groupId:artifactId[=version]")
    process.exit(2)
  }
  if (opts.kind !== null && !["wrapper", "project"].includes(opts.kind)) {
    console.error(`Bad --kind: ${opts.kind}`)
    process.exit(2)
  }
  return opts
}

// --- Discovery ---

const MODULE_PATH_RE = /filepath="\$PROJECT_DIR\$\/([^"]+)"/g

// Wrapper module iml paths registered in a modules.xml, relative to its project root.
export function parseModulesXml(content) {
  const out = []
  for (const m of content.matchAll(MODULE_PATH_RE)) {
    const rel = m[1]
    const name = basename(rel)
    if (name.startsWith(WRAPPER_PREFIX) && name.endsWith(".iml")) out.push(rel)
  }
  return out
}

async function readOptional(path) {
  try {
    return await readFile(path, "utf8")
  } catch {
    return null
  }
}

// Every file that can pin a repository library: {path, kind}.
// The ultimate modules.xml registers the community modules too, so the same iml is deduplicated by path.
async function discoverSources(projectRoots = PROJECT_ROOTS) {
  const sources = new Map()
  for (const root of projectRoots) {
    const modulesXml = await readOptional(join(root, ".idea/modules.xml"))
    if (modulesXml) {
      for (const rel of parseModulesXml(modulesXml)) {
        const path = join(root, rel)
        if (!sources.has(path)) sources.set(path, { path, kind: "wrapper" })
      }
    }
    const librariesDir = join(root, ".idea/libraries")
    if (existsSync(librariesDir)) {
      for (const e of await readdir(librariesDir, { withFileTypes: true })) {
        if (!e.isFile() || !e.name.endsWith(".xml")) continue
        const path = join(librariesDir, e.name)
        if (!sources.has(path)) sources.set(path, { path, kind: "project" })
      }
    }
  }
  return [...sources.values()]
}

// Extract every <library name="..." type="repository">...</library> block and its maven-id.
// Robust to attribute order: the library open tag may have name and type in either order.
const LIB_BLOCK_RE = /<library\b[^>]*?\btype="repository"[^>]*>([\s\S]*?)<\/library>/g
const LIB_NAME_RE = /\bname="([^"]+)"/
const MAVEN_ID_RE = /\bmaven-id="([^"]+)"/

export function parseIml(content, filePath, kind = "wrapper") {
  const out = []
  for (const m of content.matchAll(LIB_BLOCK_RE)) {
    const openTagEnd = content.indexOf(">", m.index)
    const openTag = content.slice(m.index, openTagEnd + 1)
    const nameMatch = openTag.match(LIB_NAME_RE)
    const body = m[1]
    const idMatch = body.match(MAVEN_ID_RE)
    if (!idMatch) continue
    const parts = idMatch[1].split(":")
    if (parts.length < 3) continue
    const [groupId, artifactId, version] = parts
    out.push({
      groupId,
      artifactId,
      version,
      libraryName: nameMatch ? nameMatch[1] : artifactId,
      filePath,
      kind,
    })
  }
  return out
}

async function collectLibraries() {
  const all = []
  for (const source of await discoverSources()) {
    const content = await readOptional(source.path)
    if (content === null) continue
    for (const lib of parseIml(content, source.path, source.kind)) all.push(lib)
  }
  return all
}

function sourceName(entry) {
  if (entry.kind === "project") return entry.libraryName
  return basename(entry.filePath).replace(/^intellij\.libraries\./, "").replace(/\.iml$/, "")
}

export function groupByGA(entries) {
  const map = new Map()
  for (const e of entries) {
    const key = `${e.groupId}:${e.artifactId}`
    let group = map.get(key)
    if (!group) {
      group = {
        groupId: e.groupId,
        artifactId: e.artifactId,
        versions: new Set(),
        modules: [],
      }
      map.set(key, group)
    }
    group.versions.add(e.version)
    group.modules.push({
      module: sourceName(e),
      kind: e.kind,
      version: e.version,
      libraryName: e.libraryName,
      path: e.filePath,
    })
  }
  return [...map.values()].map(g => ({
    ...g,
    versions: [...g.versions].sort(compareVersions),
    modules: g.modules.sort((a, b) => a.module.localeCompare(b.module)),
  }))
}

// --- Version parsing / comparison ---

const RELEASE_MARKERS = new Set(["final", "release", "ga"])
const PRERELEASE_TOKEN_RE = /^(?:alpha|beta|rc|cr|ea|eap|milestone|snapshot|preview|dev|pr|m|nightly)\d*$/i
// The order of prerelease stages from the earliest to the latest.
const PRERELEASE_RANK = ["dev", "snapshot", "nightly", "ea", "eap", "alpha", "beta", "milestone", "m", "preview", "pr", "rc", "cr"]
const FORK_TOKEN_RE = /^(?:jetbrains|intellij|jb\d*|idea\d*|patched|amn)$/i
const FORK_GROUP_PREFIX = "org.jetbrains.intellij.deps"
const VERSION_RE = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?(?:[.\-+_](.+))?$/

export function parseVersion(v) {
  if (!v) return null
  const m = String(v).match(VERSION_RE)
  if (!m) return null
  const [, a, b, c, d, suffix = ""] = m
  const tokens = []
  for (const raw of suffix.split(/[.\-+_]/)) {
    if (!raw) continue
    if (RELEASE_MARKERS.has(raw.toLowerCase())) continue
    tokens.push({
      raw,
      num: /^\d+$/.test(raw) ? Number(raw) : null,
      pre: PRERELEASE_TOKEN_RE.test(raw),
      fork: FORK_TOKEN_RE.test(raw),
    })
  }
  return {
    parts: [a, b, c, d].map(x => (x == null ? 0 : Number(x))),
    suffix,
    tokens,
    prerelease: tokens.some(t => t.pre),
    fork: tokens.some(t => t.fork),
    // Alpha tokens that name a variant (jre, jdk5, r, x-compat), never a stage or a fork marker.
    family: tokens.filter(t => t.num === null && !t.pre && !t.fork).map(t => t.raw.toLowerCase()).join("-"),
    raw: String(v),
  }
}

function prereleaseRank(token) {
  const word = token.raw.toLowerCase().replace(/\d+$/, "")
  const i = PRERELEASE_RANK.indexOf(word)
  return i === -1 ? PRERELEASE_RANK.length : i
}

function compareTokens(ta, tb) {
  if (!ta && !tb) return 0
  if (!ta) return tb.pre ? 1 : -1 // a release beats a prerelease; "1.5.7" is below "1.5.7-12"
  if (!tb) return ta.pre ? -1 : 1
  if (ta.num !== null && tb.num !== null) return ta.num - tb.num
  if (ta.num !== null) return 1
  if (tb.num !== null) return -1
  if (ta.pre && tb.pre) {
    const r = prereleaseRank(ta) - prereleaseRank(tb)
    if (r !== 0) return r
    const na = Number(ta.raw.match(/\d+$/)?.[0] ?? 0)
    const nb = Number(tb.raw.match(/\d+$/)?.[0] ?? 0)
    if (na !== nb) return na - nb
  }
  return ta.raw.toLowerCase().localeCompare(tb.raw.toLowerCase())
}

export function compareVersions(a, b) {
  const pa = typeof a === "string" ? parseVersion(a) : a
  const pb = typeof b === "string" ? parseVersion(b) : b
  if (!pa || !pb) return String(a).localeCompare(String(b))
  for (let i = 0; i < 4; i++) {
    if (pa.parts[i] !== pb.parts[i]) return pa.parts[i] - pb.parts[i]
  }
  const n = Math.max(pa.tokens.length, pb.tokens.length)
  for (let i = 0; i < n; i++) {
    const r = compareTokens(pa.tokens[i], pb.tokens[i])
    if (r !== 0) return r
  }
  return 0
}

export function detectFork(groupId, version) {
  if (groupId.startsWith(FORK_GROUP_PREFIX)) return true
  const p = parseVersion(version)
  return !!p && p.fork
}

export function classify(current, latest) {
  if (!latest) return "unknown"
  if (current === latest) return "up-to-date"
  const cv = parseVersion(current)
  const lv = parseVersion(latest)
  if (!cv || !lv) return "unknown"
  for (let i = 0; i < 4; i++) {
    if (cv.parts[i] > lv.parts[i]) return "ahead"
    if (cv.parts[i] < lv.parts[i]) {
      if (i === 0) return "major"
      if (i === 1) return "minor"
      return "patch"
    }
  }
  const r = compareVersions(cv, lv)
  if (r === 0) return "up-to-date"
  return r > 0 ? "ahead" : "patch"
}

function maxVersion(parsed) {
  let best = null
  for (const p of parsed) if (!best || compareVersions(p, best) > 0) best = p
  return best
}

// Select the latest version for the current pin: same variant family, stable unless the pin is a prerelease.
export function pickLatest(versions, current) {
  const cur = parseVersion(current)
  const family = cur ? cur.family : ""
  const pool = versions.map(parseVersion).filter(p => p && !p.fork && p.family === family)
  const stable = pool.filter(p => !p.prerelease)
  if (stable.length > 0) return { latest: maxVersion(stable).raw, note: null }
  if (pool.length > 0) {
    const top = maxVersion(pool).raw
    if (cur && cur.prerelease) return { latest: top, note: null }
    return { latest: null, note: `only prereleases: ${top}` }
  }
  if (versions.length > 0) return { latest: null, note: `no release for variant "${family || "plain"}"` }
  return { latest: null, note: null }
}

// --- Cache ---

async function loadCache(useCache) {
  if (!useCache || !existsSync(CACHE_PATH)) return new Map()
  try {
    const raw = JSON.parse(await readFile(CACHE_PATH, "utf8"))
    if (raw.version !== CACHE_VERSION) return new Map()
    const now = Date.now()
    const m = new Map()
    for (const [k, v] of Object.entries(raw.entries || {})) {
      if (v && typeof v.fetchedAt === "number" && now - v.fetchedAt < CACHE_TTL_MS) {
        m.set(k, v)
      }
    }
    return m
  } catch {
    return new Map()
  }
}

async function saveCache(map) {
  const entries = Object.fromEntries(map)
  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(CACHE_PATH, JSON.stringify({ version: CACHE_VERSION, entries }, null, 2))
}

// --- Repositories ---

const REPO_URL_RE = /<option name="url" value="([^"]+)"/g

export function parseRepositories(xml) {
  const out = []
  for (const m of xml.matchAll(REPO_URL_RE)) out.push(m[1].replace(/\/+$/, ""))
  return out
}

// Maven Central first, then every repository the IDE project knows, in file order.
// The build downloads through the project's Central mirror, so that mirror serves the bump checksums.
async function loadRepositories(projectRoots = PROJECT_ROOTS) {
  const repos = [MAVEN_CENTRAL]
  let centralMirror = null
  for (const root of projectRoots) {
    const xml = await readOptional(join(root, ".idea/jarRepositories.xml"))
    if (!xml) continue
    for (const url of parseRepositories(xml)) {
      if (url.endsWith("repo1.maven.org/maven2")) {
        centralMirror ??= url
        continue
      }
      if (!repos.includes(url)) repos.push(url)
    }
  }
  return { repos, centralMirror }
}

export function repoLabel(url) {
  if (!url) return "—"
  if (url === MAVEN_CENTRAL || url.endsWith("repo1.maven.org/maven2")) return "central"
  const path = url.replace(/^https?:\/\//, "").replace(/^cache-redirector\.jetbrains\.com\//, "")
  const space = path.match(/\/(?:maven|public)\/p\/(.+)$/)
  if (space) return space[1]
  if (path.startsWith("dl.google.com/")) return "google"
  return path.split("/")[0]
}

// --- Network ---

async function fetchWithTimeout(url, init = {}) {
  return fetch(url, {
    ...init,
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    headers: {
      "user-agent": "intellij-libraries-dashboard/2.0",
      ...(init.headers || {}),
    },
  })
}

async function fetchText(url) {
  try {
    const r = await fetchWithTimeout(url)
    if (!r.ok) return null
    return await r.text()
  } catch {
    return null
  }
}

const META_VERSION_RE = /<version>([^<]+)<\/version>/g

function artifactPath(groupId, artifactId) {
  return `${groupId.replaceAll(".", "/")}/${artifactId}`
}

// Try the repositories in order. The first one that yields a latest version wins; when every repository
// serves only prereleases or other variants, the first one with a version list is the answer.
async function resolveVersions(groupId, artifactId, repos, current) {
  let fallback = null
  for (const repo of repos) {
    const xml = await fetchText(`${repo}/${artifactPath(groupId, artifactId)}/maven-metadata.xml`)
    if (!xml) continue
    const versions = []
    for (const m of xml.matchAll(META_VERSION_RE)) versions.push(m[1].trim())
    if (versions.length === 0) continue
    const pick = pickLatest(versions, current)
    if (pick.latest) return { repo, pick }
    fallback ??= { repo, pick }
  }
  return fallback
}

const POM_URL_RE = /<url>\s*(https:\/\/github\.com\/[^<\s]+?)\s*<\/url>/i
const POM_SCM_RE =
  /<scm>[\s\S]*?<(?:url|connection|developerConnection)>\s*([^<]*?github\.com[^<]+?)\s*<\/(?:url|connection|developerConnection)>[\s\S]*?<\/scm>/i

export function normalizeGithubUrl(raw) {
  const u = raw.replace(/^scm:git:/, "").replace(/^git\+/, "").replace(/^git:\/\//, "https://")
  const m = u.match(/github\.com[/:]([^/\s]+)\/([^/\s]+?)(?:\.git)?(?:\/|$)/i)
  if (!m) return null
  return `https://github.com/${m[1]}/${m[2]}`
}

function pomUrl(repo, groupId, artifactId, version) {
  return `${repo}/${artifactPath(groupId, artifactId)}/${version}/${artifactId}-${version}.pom`
}

export function githubUrlFromPom(pom) {
  const scm = pom.match(POM_SCM_RE)
  if (scm) {
    const n = normalizeGithubUrl(scm[1])
    if (n) return n
  }
  const u = pom.match(POM_URL_RE)
  if (u) {
    const n = normalizeGithubUrl(u[1])
    if (n) return n
  }
  return null
}

async function fetchGithubUrl(repo, groupId, artifactId, version) {
  const pom = await fetchText(pomUrl(repo, groupId, artifactId, version))
  return pom ? githubUrlFromPom(pom) : null
}

async function runPool(items, workerFn, concurrency) {
  const queue = items.slice()
  const results = []
  async function worker() {
    while (queue.length) {
      const item = queue.shift()
      results.push(await workerFn(item))
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker))
  return results
}

// Fill g.latest, g.githubUrl, g.repo, g.note from the network and record the result in the cache.
async function resolveArtifact(g, repos, cache) {
  const current = g.versions[g.versions.length - 1]
  const orderedRepos = g.repo ? [g.repo, ...repos.filter(r => r !== g.repo)] : repos
  const resolved = await resolveVersions(g.groupId, g.artifactId, orderedRepos, current)
  if (resolved) {
    g.repo = resolved.repo
    g.latest = resolved.pick.latest
    g.note = resolved.pick.note
    g.githubUrl = await fetchGithubUrl(resolved.repo, g.groupId, g.artifactId, current)
  } else {
    g.repo = null
    g.latest = null
    g.note = null
    g.githubUrl = null
  }
  cache.set(`${g.groupId}:${g.artifactId}`, {
    latest: g.latest,
    githubUrl: g.githubUrl,
    repo: g.repo,
    note: g.note,
    fetchedAt: Date.now(),
  })
}

function applyCached(g, cached) {
  g.latest = cached.latest
  g.githubUrl = cached.githubUrl
  g.repo = cached.repo ?? null
  g.note = cached.note ?? null
}

async function enrich(groups, cache, repos, opts) {
  const toFetch = []
  for (const g of groups) {
    const key = `${g.groupId}:${g.artifactId}`
    const cached = cache.get(key)
    if (cached && !opts.refresh && cached.latest !== undefined) {
      applyCached(g, cached)
    } else {
      if (cached && cached.repo) g.repo = cached.repo
      toFetch.push(g)
    }
  }

  let done = 0
  const total = toFetch.length
  if (total > 0) {
    process.stderr.write(`Fetching metadata for ${total} artifacts from ${repos.length} repositories...\n`)
  }

  await runPool(
    toFetch,
    async g => {
      await resolveArtifact(g, repos, cache)
      done++
      if (done % 10 === 0 || done === total) {
        process.stderr.write(`  ${done}/${total}\n`)
      }
    },
    FETCH_CONCURRENCY
  )

  for (const g of groups) {
    const current = g.versions[g.versions.length - 1]
    g.fork = detectFork(g.groupId, current)
    g.statusVsLatest = classify(current, g.latest)
    if (g.versions.length > 1) g.status = "inconsistent"
    else if (g.fork) g.status = "fork"
    else g.status = g.statusVsLatest
  }
}

// --- Bump ---

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

const ARTIFACT_SHA_RE = /(<artifact url="([^"]+)">\s*<sha256sum>)([0-9a-fA-F]+)(<\/sha256sum>)/g

// Rewrite one <library> block from oldVersion to newVersion.
// Only the maven-id and the URLs that carry oldVersion change; roots pinned to another version stay.
// Returns the new block and the <artifact> URLs whose checksum must be refreshed.
export function rewriteLibraryBlock(block, { groupId, artifactId, oldVersion, newVersion }) {
  const ga = `${groupId}:${artifactId}`
  const oldId = `maven-id="${ga}:${oldVersion}"`
  if (!block.includes(oldId)) throw new Error(`block has no ${oldId}`)
  let out = block.replace(oldId, `maven-id="${ga}:${newVersion}"`)
  const old = escapeRegExp(oldVersion)
  const urlRe = new RegExp(`/${old}/([^/"]+?)-${old}(?=[.-])`, "g")
  out = out.replace(urlRe, `/${newVersion}/$1-${newVersion}`)
  const artifactUrls = []
  for (const m of out.matchAll(ARTIFACT_SHA_RE)) artifactUrls.push(m[2])
  return { block: out, artifactUrls }
}

export function applyChecksums(block, checksums) {
  return block.replace(ARTIFACT_SHA_RE, (whole, open, url, oldSum, close) => {
    const sum = checksums.get(url)
    return sum ? `${open}${sum}${close}` : whole
  })
}

// file://$MAVEN_REPOSITORY$/g/a/v/a-v.jar -> https://repo/g/a/v/a-v.jar
export function repositoryUrl(artifactUrl, repo) {
  const rel = artifactUrl.replace(/^file:\/\/\$MAVEN_REPOSITORY\$\//, "")
  return `${repo}/${rel}`
}

// Fetch the .sha256 next to the jar; hash the jar itself when the repository has none.
export async function fetchSha256(artifactUrl, repo) {
  const url = repositoryUrl(artifactUrl, repo)
  const text = await fetchText(`${url}.sha256`)
  const m = text && text.match(/[0-9a-fA-F]{64}/)
  if (m) return m[0].toLowerCase()
  try {
    const r = await fetchWithTimeout(url)
    if (!r.ok) return null
    return createHash("sha256").update(Buffer.from(await r.arrayBuffer())).digest("hex")
  } catch {
    return null
  }
}

const POM_DEPENDENCY_RE = /<dependency>([\s\S]*?)<\/dependency>/g

function tag(xml, name) {
  return xml.match(new RegExp(`<${name}>\\s*([^<]*?)\\s*</${name}>`))?.[1] ?? null
}

// --- POM direct dependencies and the artifact snapshot check ---
//
// JPS resolves a repository library from its POM and then requires the resolved jar set to equal the
// <verification> artifact set (DependencyResolvingBuilder.isAllCompiledRootsVerificationPresent). A transitive
// artifact pinned at another version than the POM declares, or a direct dependency without an <artifact>,
// fails every JPS build while Bazel, which downloads the listed URLs, still passes. The check below compares
// the direct compile and runtime dependencies of the POM with the block; it does not walk deeper levels.

// The sections of a POM that hold <version> and <groupId> tags of other artifacts.
const POM_FOREIGN_SECTIONS_RE = /<(parent|dependencyManagement|dependencies|build|profiles|reporting|pluginRepositories|repositories)>[\s\S]*?<\/\1>/g

// A POM without its XML comments; a commented-out dependency is not a dependency.
function stripXmlComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, "")
}

// The groupId and version of the POM's own project; the parent supplies a missing one.
export function pomProjectCoordinates(pom) {
  const clean = stripXmlComments(pom)
  const own = clean.replace(POM_FOREIGN_SECTIONS_RE, "")
  const parent = clean.match(/<parent>([\s\S]*?)<\/parent>/)?.[1] ?? ""
  return {
    groupId: tag(own, "groupId") ?? tag(parent, "groupId"),
    version: tag(own, "version") ?? tag(parent, "version"),
  }
}

// Resolve `${...}` references from the POM's own <properties>, project.version and project.groupId.
// Returns null when a reference stays unresolved, for example a property of the parent POM.
export function resolvePomValue(value, pom) {
  if (value === null) return null
  const project = pomProjectCoordinates(pom)
  const properties = stripXmlComments(pom).match(/<properties>([\s\S]*?)<\/properties>/)?.[1] ?? ""
  let unresolved = false
  const out = value.replace(/\$\{([^}]+)}/g, (whole, name) => {
    if (name === "project.version" || name === "version" || name === "pom.version") return project.version ?? (unresolved = true, whole)
    if (name === "project.groupId" || name === "groupId" || name === "pom.groupId") return project.groupId ?? (unresolved = true, whole)
    const v = tag(properties, name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    if (v === null) {
      unresolved = true
      return whole
    }
    return v
  })
  return unresolved ? null : out
}

// A Maven version requirement as one version: `[1.2.0]` is exactly 1.2.0; an open range is null.
export function exactVersion(requirement) {
  if (requirement === null) return null
  const hard = requirement.match(/^\[\s*([^,[\]()]+?)\s*]$/)
  if (hard) return hard[1]
  return /^[\[(]/.test(requirement) ? null : requirement
}

// The sections of a POM whose <dependencies> are not dependencies of the artifact.
const POM_NON_DEPENDENCY_SECTIONS_RE = /<(profiles|build|reporting|dependencyManagement)>[\s\S]*?<\/\1>/g

// The direct compile and runtime jar dependencies of a POM: [{groupId, artifactId, version, scope, classifier}].
// The version is null when a reference cannot be resolved from this POM. A dependency without a version is
// managed by a parent or an import and is null too. <dependencyManagement>, plugin and profile entries are not dependencies.
export function pomDirectDependencies(pom) {
  const clean = stripXmlComments(pom)
  const section = clean.replace(POM_NON_DEPENDENCY_SECTIONS_RE, "").match(/<dependencies>([\s\S]*?)<\/dependencies>/)?.[1]
  if (!section) return []
  const out = []
  for (const m of section.matchAll(POM_DEPENDENCY_RE)) {
    const scope = tag(m[1], "scope") || "compile"
    if (scope === "test" || scope === "provided" || scope === "system" || tag(m[1], "optional") === "true") continue
    const type = tag(m[1], "type")
    if (type !== null && type !== "jar") continue
    out.push({
      groupId: resolvePomValue(tag(m[1], "groupId"), clean),
      artifactId: resolvePomValue(tag(m[1], "artifactId"), clean),
      version: exactVersion(resolvePomValue(tag(m[1], "version"), clean)),
      scope,
      classifier: tag(m[1], "classifier"),
    })
  }
  return out
}

// compile and runtime dependencies of a POM, as "groupId:artifactId:version (scope)".
export function pomDependencies(pom) {
  return pomDirectDependencies(pom).map(d => `${d.groupId}:${d.artifactId}:${d.version ?? "?"} (${d.scope})`)
}

const ARTIFACT_URL_RE = /<artifact url="file:\/\/\$MAVEN_REPOSITORY\$\/(.+)\/([^/"]+)\/([^/"]+)\/[^/"]+\.jar"/g
const EXCLUDED_DEPENDENCY_RE = /<dependency maven-id="([^"]+)"/g

// The <artifact> coordinates of a library block: [{groupId, artifactId, version}].
export function blockArtifacts(block) {
  const out = []
  for (const m of block.matchAll(ARTIFACT_URL_RE)) out.push({ groupId: m[1].replaceAll("/", "."), artifactId: m[2], version: m[3] })
  return out
}

// The library block of a file whose maven-id is `groupId:artifactId:version`, or null.
export function findLibraryBlock(content, mavenId) {
  for (const b of content.matchAll(LIB_BLOCK_RE)) {
    if (b[1].includes(`maven-id="${mavenId}"`)) return b[0]
  }
  return null
}

/**
 * Compare the <artifact> snapshot of one library block with the direct dependencies of its POM.
 * Returns `{problems, notes}`. A problem is `{kind: "version", ...}` when an artifact of a direct dependency is
 * pinned at another version than the POM declares, or `{kind: "missing", ...}` when a direct dependency has no
 * artifact and is not excluded. A note names a dependency whose version this POM cannot resolve. A block with
 * `include-transitive-deps="false"` has nothing to compare.
 */
export function checkArtifactSnapshot(block, deps) {
  const problems = []
  const notes = []
  if (/include-transitive-deps="false"/.test(block)) return { problems, notes }
  const artifacts = blockArtifacts(block)
  const excluded = new Set()
  const exclude = block.match(/<exclude>([\s\S]*?)<\/exclude>/)?.[1] ?? ""
  for (const m of exclude.matchAll(EXCLUDED_DEPENDENCY_RE)) excluded.add(m[1])
  for (const d of deps) {
    const ga = `${d.groupId}:${d.artifactId}`
    if (excluded.has(ga) || d.classifier !== null) continue
    if (d.version === null) {
      notes.push(`${ga}: the POM does not name one exact version (a parent property or a range); compare it by hand`)
      continue
    }
    const artifact = artifacts.find(a => a.groupId === d.groupId && a.artifactId === d.artifactId)
    if (!artifact) problems.push({ kind: "missing", groupId: d.groupId, artifactId: d.artifactId, declared: d.version, scope: d.scope })
    else if (artifact.version !== d.version) problems.push({ kind: "version", groupId: d.groupId, artifactId: d.artifactId, pinned: artifact.version, declared: d.version, scope: d.scope })
  }
  return { problems, notes }
}

export const SNAPSHOT_REMEDY =
  "JPS resolves the artifact set from the POM and requires the <verification> list to match it. " +
  "Pin the version the POM declares, or exclude the dependency and depend on its wrapper module."

export function formatSnapshotProblem(p) {
  const ga = `${p.groupId}:${p.artifactId}`
  return p.kind === "version"
    ? `${ga} is pinned at ${p.pinned}, the POM declares ${p.declared} (${p.scope})`
    : `${ga}:${p.declared} (${p.scope}) is a direct dependency without an <artifact> and without an <exclude>`
}

function relPath(path) {
  return path.startsWith(REPO_ROOT + "/") ? path.slice(REPO_ROOT.length + 1) : path
}

export function parseCoordinate(arg) {
  const [ga, version] = arg.split("=")
  const parts = ga.split(":")
  if (parts.length !== 2 || !parts[0] || !parts[1]) throw new Error(`bad coordinate: ${arg} (expected groupId:artifactId[=version])`)
  return { groupId: parts[0], artifactId: parts[1], version: version || null }
}

/**
 * Files outside the JPS model that copy a library version. A project structure test checks each copy, so a bump
 * that skips one fails the Smoke Tests build. `pom`: every `<dependency>` of the file whose groupId:artifactId is
 * pinned in the JPS model, unless its version is a property. `constant`: one literal in one source file; the
 * second capture group of `pattern` is the version.
 */
export const VERSION_MIRRORS = [
  {
    kind: "pom",
    path: "community/platform/jps-bootstrap/pom.xml",
    test: "com.intellij.ideaProjectStructure.fast.JpsBoostrapStructureTest",
  },
  {
    kind: "constant",
    path: "community/java/java-impl/src/com/intellij/codeInsight/daemon/impl/quickfix/JetBrainsAnnotationsExternalLibraryResolver.java",
    groupId: "org.jetbrains",
    artifactId: "annotations",
    pattern: /(private static final String VERSION = ")([^"]+)(")/,
    test: "com.intellij.ideaProjectStructure.fast.IdeaUltimateProjectStructureTest",
  },
]

export const STRUCTURE_TESTS_COMMAND =
  "./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'"

// The version copies of one mirror file: [{groupId, artifactId, version}]. A property reference is not a copy.
export function mirrorVersions(content, mirror) {
  if (mirror.kind === "constant") {
    const m = content.match(mirror.pattern)
    return m ? [{ groupId: mirror.groupId, artifactId: mirror.artifactId, version: m[2] }] : []
  }
  const out = []
  for (const m of content.matchAll(POM_DEPENDENCY_RE)) {
    const version = tag(m[1], "version")
    if (version === null || version.startsWith("${")) continue
    out.push({ groupId: tag(m[1], "groupId"), artifactId: tag(m[1], "artifactId"), version })
  }
  return out
}

// Rewrite the copies of groupId:artifactId in one mirror file. Returns null when the file has no copy.
export function rewriteMirror(content, mirror, { groupId, artifactId, newVersion }) {
  const copies = mirrorVersions(content, mirror).filter(c => c.groupId === groupId && c.artifactId === artifactId)
  if (copies.length === 0) return null
  const from = copies[0].version
  if (mirror.kind === "constant") {
    return { content: content.replace(mirror.pattern, `$1${newVersion}$3`), from }
  }
  const out = content.replace(POM_DEPENDENCY_RE, whole => {
    if (tag(whole, "groupId") !== groupId || tag(whole, "artifactId") !== artifactId) return whole
    const version = tag(whole, "version")
    if (version === null || version.startsWith("${")) return whole
    return whole.replace(/(<version>\s*)[^<]*?(\s*<\/version>)/, `$1${newVersion}$2`)
  })
  return { content: out, from }
}

// The copies whose version differs from the JPS model: [{path, groupId, artifactId, version, pinned}].
export function mirrorDrift(content, mirror, pinnedVersions) {
  const out = []
  for (const c of mirrorVersions(content, mirror)) {
    const pinned = pinnedVersions.get(`${c.groupId}:${c.artifactId}`)
    if (pinned && pinned !== c.version) out.push({ path: mirror.path, ...c, pinned })
  }
  return out
}

async function bumpMirrors(g, target) {
  const changed = []
  for (const mirror of VERSION_MIRRORS) {
    const path = join(REPO_ROOT, mirror.path)
    const content = await readOptional(path)
    if (content === null) continue
    const result = rewriteMirror(content, mirror, { groupId: g.groupId, artifactId: g.artifactId, newVersion: target })
    if (!result || result.content === content) continue
    await writeFile(path, result.content)
    changed.push({ path, from: result.from, test: mirror.test })
  }
  return changed
}

// The highest pinned version of every group, for the drift check.
function pinnedVersions(groups) {
  return new Map(groups.map(g => [`${g.groupId}:${g.artifactId}`, g.versions[g.versions.length - 1]]))
}

async function reportMirrorDrift(groups) {
  const pinned = pinnedVersions(groups)
  const drift = []
  for (const mirror of VERSION_MIRRORS) {
    const content = await readOptional(join(REPO_ROOT, mirror.path))
    if (content !== null) drift.push(...mirrorDrift(content, mirror, pinned))
  }
  if (drift.length === 0) return false
  console.error(`\nVersion copies outside the JPS model differ from the pinned versions (${STRUCTURE_TESTS_COMMAND} fails):`)
  for (const d of drift) console.error(`  ${d.path}: ${d.groupId}:${d.artifactId} ${d.version}, pinned ${d.pinned}`)
  return true
}

// Rewrite the library block of one source file in memory. Returns the new content and the artifact count.
export async function bumpFile(content, { groupId, artifactId, oldVersion, newVersion }, checksumFor) {
  const oldId = `maven-id="${groupId}:${artifactId}:${oldVersion}"`
  for (const b of content.matchAll(LIB_BLOCK_RE)) {
    if (!b[1].includes(oldId)) continue
    const rewritten = rewriteLibraryBlock(b[0], { groupId, artifactId, oldVersion, newVersion })
    const checksums = new Map()
    for (const url of rewritten.artifactUrls) {
      const sum = await checksumFor(url)
      if (!sum) throw new Error(`no sha256 for ${url}`)
      checksums.set(url, sum)
    }
    const block = applyChecksums(rewritten.block, checksums)
    return {
      content: content.slice(0, b.index) + block + content.slice(b.index + b[0].length),
      artifacts: rewritten.artifactUrls.length,
    }
  }
  throw new Error(`no repository library ${groupId}:${artifactId}:${oldVersion}`)
}

// Bump every source file of the group; all files are rewritten in memory first, so a failed checksum changes nothing.
async function bumpGroup(g, target, kind, checksumRepo) {
  const pending = []
  for (const m of g.modules) {
    if (m.version === target) continue
    if (kind && m.kind !== kind) continue
    const content = await readFile(m.path, "utf8")
    let result
    try {
      result = await bumpFile(
        content,
        { groupId: g.groupId, artifactId: g.artifactId, oldVersion: m.version, newVersion: target },
        url => fetchSha256(url, checksumRepo)
      )
    } catch (e) {
      throw new Error(`${relPath(m.path)}: ${e.message}`)
    }
    pending.push({ path: m.path, from: m.version, artifacts: result.artifacts, content: result.content })
  }
  for (const p of pending) await writeFile(p.path, p.content)
  return pending.map(({ content, ...rest }) => rest)
}

// Check the rewritten blocks of one library against its new POM. Returns true when a block has a problem.
async function reportSnapshotProblems(changed, mavenId, pom) {
  const deps = pomDirectDependencies(pom)
  let failed = false
  for (const c of changed) {
    const block = findLibraryBlock(await readFile(c.path, "utf8"), mavenId)
    if (!block) continue
    const { problems, notes } = checkArtifactSnapshot(block, deps)
    for (const n of notes) console.log(`  ${relPath(c.path)}: ${n}`)
    if (problems.length === 0) continue
    failed = true
    console.error(`  ${relPath(c.path)}: the artifact list does not match the POM. ${SNAPSHOT_REMEDY}`)
    for (const p of problems) console.error(`    ${formatSnapshotProblem(p)}`)
  }
  return failed
}

// The POM of a pinned library: the local Maven repository first, then the cached repository, then every repository.
async function loadPom(entry, repos, cache) {
  const rel = `${artifactPath(entry.groupId, entry.artifactId)}/${entry.version}/${entry.artifactId}-${entry.version}.pom`
  const local = await readOptional(join(localMavenRepository(), rel))
  if (local !== null) return local
  const cached = cache.get(`${entry.groupId}:${entry.artifactId}`)?.repo
  for (const repo of new Set([cached, ...repos].filter(Boolean))) {
    const pom = await fetchText(`${repo}/${rel}`)
    if (pom !== null) return pom
  }
  return null
}

function localMavenRepository() {
  return process.env.MAVEN_REPOSITORY || join(homedir(), ".m2/repository")
}

// `check`: compare every multi-artifact library block, or the blocks of the named libraries, with its POM.
async function runCheck(opts) {
  const wanted = new Set(opts.coordinates.map(parseCoordinate).map(t => `${t.groupId}:${t.artifactId}`))
  const entries = (await collectLibraries()).filter(e => wanted.size === 0 || wanted.has(`${e.groupId}:${e.artifactId}`))
  for (const ga of wanted) {
    if (!entries.some(e => `${e.groupId}:${e.artifactId}` === ga)) {
      console.error(`Not pinned anywhere: ${ga}`)
      process.exit(2)
    }
  }
  const blocks = []
  const contents = new Map()
  for (const e of entries) {
    if (opts.kind && e.kind !== opts.kind) continue
    if (!contents.has(e.filePath)) contents.set(e.filePath, await readFile(e.filePath, "utf8"))
    const block = findLibraryBlock(contents.get(e.filePath), `${e.groupId}:${e.artifactId}:${e.version}`)
    if (!block || blockArtifacts(block).length < 2 || /include-transitive-deps="false"/.test(block)) continue
    blocks.push({ ...e, block })
  }
  const cache = await loadCache(opts.cache && !opts.refresh)
  const { repos } = await loadRepositories()
  let problems = 0
  let unavailable = 0
  let notes = 0
  await runPool(
    blocks,
    async b => {
      const pom = await loadPom(b, repos, cache)
      const mavenId = `${b.groupId}:${b.artifactId}:${b.version}`
      if (pom === null) {
        unavailable++
        console.error(`${relPath(b.filePath)}: ${mavenId}: POM not available`)
        return
      }
      const result = checkArtifactSnapshot(b.block, pomDirectDependencies(pom))
      notes += result.notes.length
      // A whole-repository run lists only the problems; a named library gets its notes too.
      if (wanted.size > 0) for (const n of result.notes) console.log(`${relPath(b.filePath)}: ${mavenId}: ${n}`)
      if (result.problems.length === 0) return
      problems += result.problems.length
      console.error(`${relPath(b.filePath)}: ${mavenId}: the artifact list does not match the POM`)
      for (const p of result.problems) console.error(`  ${formatSnapshotProblem(p)}`)
    },
    FETCH_CONCURRENCY
  )
  const summary = [`${problems} problem${problems === 1 ? "" : "s"}`]
  if (notes > 0) summary.push(`${notes} dependenc${notes === 1 ? "y" : "ies"} without one exact version in the POM, not compared${wanted.size > 0 ? "" : " (name the library to list them)"}`)
  if (unavailable > 0) summary.push(`${unavailable} POM${unavailable === 1 ? "" : "s"} not available`)
  console.log(`\nChecked ${blocks.length} multi-artifact librar${blocks.length === 1 ? "y" : "ies"}: ${summary.join(", ")}`)
  if (problems > 0) {
    console.error(SNAPSHOT_REMEDY)
    process.exit(1)
  }
}

async function runBump(opts) {
  const targets = opts.coordinates.map(parseCoordinate)
  const entries = await collectLibraries()
  const groups = groupByGA(entries)
  const byKey = new Map(groups.map(g => [`${g.groupId}:${g.artifactId}`, g]))
  const selected = []
  for (const t of targets) {
    const g = byKey.get(`${t.groupId}:${t.artifactId}`)
    if (!g) {
      console.error(`Not pinned anywhere: ${t.groupId}:${t.artifactId}`)
      process.exit(2)
    }
    g.requestedVersion = t.version
    selected.push(g)
  }

  const cache = await loadCache(opts.cache && !opts.refresh)
  const { repos, centralMirror } = await loadRepositories()
  await enrich(selected, cache, repos, opts)
  await saveCache(cache)

  let failed = false
  for (const g of selected) {
    const ga = `${g.groupId}:${g.artifactId}`
    const target = g.requestedVersion ?? g.latest
    const current = g.versions[g.versions.length - 1]
    // A release that Central has but the mirror has not cached yet fails here, not in the build.
    const checksumRepo = g.repo === MAVEN_CENTRAL && centralMirror ? centralMirror : g.repo
    if (!g.repo) {
      console.error(`${ga}: no repository serves this artifact, cannot fetch checksums`)
      failed = true
      continue
    }
    if (!target) {
      console.error(`${ga}: no target version (${g.note ?? "unresolved"}); pass ${ga}=<version>`)
      failed = true
      continue
    }
    if (g.versions.length === 1 && current === target) {
      console.log(`${ga}: already at ${target}`)
      continue
    }
    try {
      const changed = await bumpGroup(g, target, opts.kind, checksumRepo)
      console.log(`${ga}: ${g.versions.join(", ")} -> ${target} (${repoLabel(g.repo)})`)
      for (const c of changed) console.log(`  ${relPath(c.path)} (${c.from} -> ${target}, ${c.artifacts} artifact${c.artifacts === 1 ? "" : "s"})`)
      for (const c of await bumpMirrors(g, target)) console.log(`  ${relPath(c.path)} (${c.from} -> ${target}, version copy checked by ${c.test})`)
      const skipped = g.modules.filter(m => m.version !== target && opts.kind && m.kind !== opts.kind)
      for (const m of skipped) console.log(`  skipped ${m.kind} library ${m.module} @${m.version} in ${relPath(m.path)} (--kind=${opts.kind})`)
      if (changed.some(c => c.artifacts > 1)) {
        const pom = await fetchText(pomUrl(g.repo, g.groupId, g.artifactId, target))
        console.log(`  the library snapshots transitive artifacts; the ${target} POM declares:`)
        for (const d of pom ? pomDependencies(pom) : ["(POM not available)"]) console.log(`    ${d}`)
        if (pom && (await reportSnapshotProblems(changed.filter(c => c.artifacts > 1), `${ga}:${target}`, pom))) failed = true
      }
    } catch (e) {
      console.error(`${ga}: ${e.message} (checksums from ${checksumRepo})`)
      failed = true
    }
  }
  if (await reportMirrorDrift(groups)) failed = true
  console.log(`\nNext: ${followUpCommands().join(" && ")}`)
  console.log(`Verify: ${STRUCTURE_TESTS_COMMAND}`)
  if (failed) process.exit(1)
}

// --- Rendering ---

export const STATUS_ORDER = ["major", "minor", "patch", "inconsistent", "unknown", "fork", "ahead", "up-to-date"]

function statusRank(s) {
  const i = STATUS_ORDER.indexOf(s)
  return i === -1 ? STATUS_ORDER.length : i
}

function htmlEscape(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]))
}

function isActionable(g) {
  return g.status !== "up-to-date" && g.status !== "ahead" && g.status !== "fork"
}

export function buildUpdatePrompt(g, repoRoot) {
  const ga = `${g.groupId}:${g.artifactId}`
  const currentMax = g.versions[g.versions.length - 1]
  const relFiles = g.modules.map(m => {
    const rel = m.path.startsWith(repoRoot + "/") ? m.path.slice(repoRoot.length + 1) : m.path
    return `  - ${rel} (${m.kind} library, currently @${m.version})`
  }).join("\n")
  if (!g.latest) {
    return [
      `Investigate the latest version of Maven library \`${ga}\`.`,
      ``,
      `It is pinned at ${currentMax} in the IntelliJ monorepo:`,
      relFiles,
      ``,
      g.note
        ? `The resolver found the artifact in ${g.repo} but ${g.note}.`
        : `No repository from .idea/jarRepositories.xml serves this artifact.`,
      `Identify the authoritative repository / release channel, determine the current stable version, and propose a version bump plan.`,
    ].join("\n")
  }
  return [
    `Update Maven library \`${ga}\` from ${currentMax} to ${g.latest} in the IntelliJ monorepo.`,
    ``,
    `Files that pin it:`,
    relFiles,
    ``,
    `Run from the repository root:`,
    `  bun community/build/libraries-dashboard/libraries-dashboard.mjs bump ${ga}=${g.latest}`,
    ...followUpCommands(repoRoot).map(c => `  ${c}`),
    ``,
    `The bump command rewrites maven-id, the jar URLs and every <sha256sum> and keeps the file layout.`,
    `It also rewrites the version copies outside the JPS model, such as community/platform/jps-bootstrap/pom.xml.`,
    `For a library that snapshots transitive artifacts it compares the artifact list with the direct dependencies of the new POM and fails on a mismatch.`,
    `JPS resolves the artifact set from the POM and requires the <verification> list to match it, so pin the declared version or exclude the dependency and depend on its wrapper module.`,
    `After a manual edit of a library block, run: bun community/build/libraries-dashboard/libraries-dashboard.mjs check ${ga}`,
    `The Fleet dump refreshes the generated Fleet files that copy the JPS library versions; do not skip it.`,
    `The two lockfile updates record the new kmp.MODULE.bazel artifact lists; CI fails on a stale lockfile.`,
    `Then build the wrapper module and run the tests of one module that depends on it.`,
    `Run the project structure tests, which check every version copy: ${STRUCTURE_TESTS_COMMAND}`,
  ].join("\n")
}

function sortedGroups(groups) {
  return groups
    .slice()
    .sort((a, b) => {
      const r = statusRank(a.status) - statusRank(b.status)
      if (r !== 0) return r
      return `${a.groupId}:${a.artifactId}`.localeCompare(`${b.groupId}:${b.artifactId}`)
    })
}

function groupKinds(g) {
  const kinds = new Set(g.modules.map(m => m.kind))
  return [...kinds].sort().join("+")
}

function renderHtml(groups, generatedAt, repoRoot) {
  const rows = sortedGroups(groups)
    .map(g => {
      const versions = g.versions.join(", ")
      const latest = g.latest ?? "—"
      const statusBadge = g.status
      const ghHref = g.githubUrl ? `${g.githubUrl}/releases/latest` : null
      const ghCell = ghHref
        ? `<a href="${htmlEscape(ghHref)}" target="_blank" rel="noopener">releases ↗</a>`
        : `<span class="muted">—</span>`
      const ga = `${g.groupId}:${g.artifactId}`
      const actionable = isActionable(g)
      const prompt = actionable ? buildUpdatePrompt(g, repoRoot) : ""
      const promptLabel = g.latest ? "Copy update prompt" : "Copy investigate prompt"
      const actionCell = actionable
        ? `<button class="copy-btn" type="button" data-prompt="${htmlEscape(prompt)}" title="${htmlEscape(promptLabel)}">📋 prompt</button>`
        : `<span class="muted">—</span>`
      const kinds = groupKinds(g)
      const latestCell = g.note
        ? `<span class="muted" title="${htmlEscape(g.note)}">${htmlEscape(latest)} ⓘ</span>`
        : htmlEscape(latest)
      return `
<tr data-status="${htmlEscape(statusBadge)}" data-kind="${htmlEscape(kinds)}" data-ga="${htmlEscape(ga.toLowerCase())}">
  <td><span class="ga">${htmlEscape(ga)}</span></td>
  <td class="mono">${htmlEscape(versions)}</td>
  <td class="mono">${latestCell}</td>
  <td><span class="badge badge-${htmlEscape(statusBadge)}">${htmlEscape(statusBadge)}</span></td>
  <td class="mono">${htmlEscape(kinds)}</td>
  <td>
    <details>
      <summary>${g.modules.length}</summary>
      <ul class="modules">${g.modules
        .map(
          m =>
            `<li><span class="mono">${htmlEscape(m.module)}</span> <span class="muted">${htmlEscape(m.kind)} @${htmlEscape(m.version)}</span></li>`
        )
        .join("")}</ul>
    </details>
  </td>
  <td class="mono" title="${htmlEscape(g.repo ?? "")}">${htmlEscape(repoLabel(g.repo))}</td>
  <td>${ghCell}</td>
  <td>${actionCell}</td>
</tr>`
    })
    .join("")

  const counts = {}
  for (const g of groups) counts[g.status] = (counts[g.status] || 0) + 1
  const countsRow = STATUS_ORDER.filter(s => counts[s])
    .map(s => `<span class="badge badge-${s}">${s}: ${counts[s]}</span>`)
    .join(" ")

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>IntelliJ Libraries Dashboard</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 14px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; padding: 24px; background: Canvas; color: CanvasText; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .meta { color: GrayText; font-size: 12px; margin-bottom: 16px; }
  .controls { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
  .controls input[type=search] { padding: 6px 10px; border: 1px solid #8886; border-radius: 6px; min-width: 240px; font: inherit; background: Canvas; color: inherit; }
  .controls select { padding: 6px 10px; border: 1px solid #8886; border-radius: 6px; font: inherit; background: Canvas; color: inherit; }
  .counts { display: flex; gap: 6px; flex-wrap: wrap; }
  table { border-collapse: collapse; width: 100%; }
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #8884; vertical-align: top; }
  th { position: sticky; top: 0; background: Canvas; cursor: pointer; user-select: none; }
  th:hover { background: #8881; }
  .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; }
  .ga { font-weight: 600; }
  .muted { color: GrayText; }
  .modules { margin: 6px 0 0; padding-left: 18px; }
  .modules li { margin: 1px 0; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; letter-spacing: 0.3px; }
  .badge-major { background: #e5484d22; color: #e5484d; }
  .badge-minor { background: #f5a62322; color: #b06d00; }
  .badge-patch { background: #3e63dd22; color: #3e63dd; }
  .badge-inconsistent { background: #8b5cf622; color: #8b5cf6; }
  .badge-up-to-date { background: #30a46c22; color: #30a46c; }
  .badge-ahead { background: #8888; color: inherit; }
  .badge-unknown { background: #8883; color: GrayText; }
  .badge-fork { background: #0ea5e922; color: #0284c7; }
  a { color: #3e63dd; text-decoration: none; }
  a:hover { text-decoration: underline; }
  .copy-btn { font: inherit; padding: 4px 10px; border: 1px solid #8886; border-radius: 6px; background: Canvas; color: inherit; cursor: pointer; white-space: nowrap; }
  .copy-btn:hover { background: #8881; }
  .copy-btn.copied { background: #30a46c22; border-color: #30a46c66; color: #30a46c; }
  .copy-btn:disabled { opacity: 0.4; cursor: not-allowed; }
  #toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); padding: 8px 14px; border-radius: 8px; background: CanvasText; color: Canvas; font-size: 13px; opacity: 0; transition: opacity 0.2s; pointer-events: none; }
  #toast.show { opacity: 0.9; }
</style>
</head>
<body>
<h1>IntelliJ Libraries Dashboard</h1>
<div class="meta">${groups.length} artifacts · generated ${htmlEscape(generatedAt)}</div>
<div class="controls">
  <input id="q" type="search" placeholder="filter by groupId:artifactId…" autofocus>
  <select id="status">
    <option value="">all statuses</option>
    ${STATUS_ORDER.map(s => `<option value="${s}">${s}</option>`).join("")}
  </select>
  <select id="kind">
    <option value="">all kinds</option>
    <option value="wrapper">wrapper modules</option>
    <option value="project">project libraries</option>
  </select>
  <div class="counts">${countsRow}</div>
</div>
<table>
  <thead>
    <tr>
      <th data-sort="ga">Artifact</th>
      <th data-sort="versions">Current</th>
      <th data-sort="latest">Latest</th>
      <th data-sort="status">Status</th>
      <th data-sort="kind">Kind</th>
      <th data-sort="modules"># sources</th>
      <th data-sort="repo">Repo</th>
      <th>GitHub</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>${rows}</tbody>
</table>
<div id="toast" role="status" aria-live="polite"></div>
<script>
const q = document.getElementById("q")
const statusSel = document.getElementById("status")
const kindSel = document.getElementById("kind")
const tbody = document.querySelector("tbody")
function apply() {
  const needle = q.value.trim().toLowerCase()
  const s = statusSel.value
  const k = kindSel.value
  for (const tr of tbody.rows) {
    const ga = tr.dataset.ga
    const st = tr.dataset.status
    const kinds = tr.dataset.kind.split("+")
    const show = (!needle || ga.includes(needle)) && (!s || st === s) && (!k || kinds.includes(k))
    tr.style.display = show ? "" : "none"
  }
}
q.addEventListener("input", apply)
statusSel.addEventListener("change", apply)
kindSel.addEventListener("change", apply)
document.querySelectorAll("th[data-sort]").forEach((th, colIdx) => {
  let asc = true
  th.addEventListener("click", () => {
    asc = !asc
    const rows = [...tbody.rows]
    rows.sort((a, b) => {
      const av = a.cells[colIdx].innerText
      const bv = b.cells[colIdx].innerText
      const na = Number(av), nb = Number(bv)
      if (!Number.isNaN(na) && !Number.isNaN(nb)) return asc ? na - nb : nb - na
      return asc ? av.localeCompare(bv) : bv.localeCompare(av)
    })
    for (const r of rows) tbody.appendChild(r)
  })
})
const toast = document.getElementById("toast")
let toastTimer
function showToast(msg) {
  toast.textContent = msg
  toast.classList.add("show")
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => toast.classList.remove("show"), 1600)
}
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const ta = document.createElement("textarea")
    ta.value = text
    ta.style.position = "fixed"
    ta.style.opacity = "0"
    document.body.appendChild(ta)
    ta.select()
    let ok = false
    try { ok = document.execCommand("copy") } catch {}
    ta.remove()
    return ok
  }
}
tbody.addEventListener("click", async (ev) => {
  const btn = ev.target.closest(".copy-btn")
  if (!btn) return
  const ok = await copyText(btn.dataset.prompt)
  if (ok) {
    btn.classList.add("copied")
    setTimeout(() => btn.classList.remove("copied"), 1200)
    showToast("Prompt copied to clipboard")
  } else {
    showToast("Copy failed — clipboard unavailable")
  }
})
</script>
</body>
</html>
`
}

// --- Terminal rendering ---

const IS_TTY = process.stdout.isTTY && !process.env.NO_COLOR
const C = IS_TTY
  ? {
      reset: "\x1b[0m",
      bold: "\x1b[1m",
      dim: "\x1b[2m",
      red: "\x1b[31m",
      green: "\x1b[32m",
      yellow: "\x1b[33m",
      blue: "\x1b[34m",
      magenta: "\x1b[35m",
      cyan: "\x1b[36m",
      gray: "\x1b[90m",
    }
  : Object.fromEntries(["reset", "bold", "dim", "red", "green", "yellow", "blue", "magenta", "cyan", "gray"].map(k => [k, ""]))

const STATUS_COLOR = {
  major: C.red,
  minor: C.yellow,
  patch: C.blue,
  inconsistent: C.magenta,
  "up-to-date": C.green,
  fork: C.cyan,
  ahead: C.gray,
  unknown: C.gray,
}

function renderTerminal(groups) {
  const sorted = sortedGroups(groups)
  const cols = [
    { title: "Artifact", get: g => `${g.groupId}:${g.artifactId}` },
    { title: "Current", get: g => g.versions.join(", ") },
    { title: "Latest", get: g => g.latest ?? (g.note ? `— (${g.note})` : "—") },
    { title: "Status", get: g => g.status, color: g => STATUS_COLOR[g.status] || "" },
    { title: "Kind", get: g => groupKinds(g) },
    { title: "#", get: g => String(g.modules.length), align: "right" },
    { title: "Repo", get: g => repoLabel(g.repo) },
    { title: "GitHub", get: g => (g.githubUrl ? g.githubUrl.replace("https://github.com/", "") : "—") },
  ]
  const widths = cols.map(c => c.title.length)
  for (const g of sorted) {
    cols.forEach((c, i) => {
      widths[i] = Math.max(widths[i], c.get(g).length)
    })
  }
  const pad = (s, w, align) => (align === "right" ? s.padStart(w) : s.padEnd(w))
  const header = cols.map((c, i) => pad(c.title, widths[i], c.align)).join("  ")
  const sep = cols.map((_, i) => "─".repeat(widths[i])).join("  ")
  const lines = [C.bold + header + C.reset, C.dim + sep + C.reset]
  for (const g of sorted) {
    const line = cols
      .map((c, i) => {
        const cell = pad(c.get(g), widths[i], c.align)
        return c.color ? c.color(g) + cell + C.reset : cell
      })
      .join("  ")
    lines.push(line)
  }
  lines.push("")
  const counts = {}
  for (const g of groups) counts[g.status] = (counts[g.status] || 0) + 1
  const summary = STATUS_ORDER.filter(s => counts[s])
    .map(s => `${STATUS_COLOR[s] || ""}${s}: ${counts[s]}${C.reset}`)
    .join("  ")
  lines.push(`${C.bold}Total:${C.reset} ${groups.length}  (${summary})`)
  return lines.join("\n")
}

// --- Main ---

async function runReport(opts) {
  const entries = await collectLibraries()
  if (entries.length === 0) {
    console.error("No repository libraries with maven-id found in modules.xml wrappers or .idea/libraries.")
    process.exit(1)
  }
  const groups = groupByGA(entries)
  await reportMirrorDrift(groups)

  const cache = await loadCache(opts.cache && !opts.refresh)
  const { repos } = await loadRepositories()
  await enrich(groups, cache, repos, opts)
  await saveCache(cache)

  const generatedAt = new Date().toISOString()

  if (opts.format === "text" || opts.format === "all") {
    console.log(renderTerminal(groups))
  }
  if (opts.format === "html" || opts.format === "all") {
    await mkdir(OUT_DIR, { recursive: true })
    await writeFile(HTML_OUT, renderHtml(groups, generatedAt, REPO_ROOT))
    console.error(`\nHTML: ${HTML_OUT}`)
  }
  if (opts.format === "json") {
    const plain = {
      generatedAt,
      repositories: repos,
      artifacts: groups.map(g => ({
        groupId: g.groupId,
        artifactId: g.artifactId,
        versions: g.versions,
        latest: g.latest,
        status: g.status,
        statusVsLatest: g.statusVsLatest,
        fork: g.fork,
        note: g.note,
        repo: g.repo,
        githubUrl: g.githubUrl,
        githubReleasesUrl: g.githubUrl ? `${g.githubUrl}/releases/latest` : null,
        modules: g.modules.map(m => ({ ...m, path: relPath(m.path) })),
      })),
    }
    await mkdir(OUT_DIR, { recursive: true })
    await writeFile(JSON_OUT, JSON.stringify(plain, null, 2))
    console.error(`JSON: ${JSON_OUT}`)
  }

  if (opts.open && (opts.format === "html" || opts.format === "all")) {
    const { spawn } = await import("node:child_process")
    const cmd = process.platform === "darwin" ? "open" : process.platform === "win32" ? "start" : "xdg-open"
    spawn(cmd, [HTML_OUT], { detached: true, stdio: "ignore" }).unref()
  }
}

async function main() {
  const opts = parseArgs(process.argv)
  if (opts.command === "bump") await runBump(opts)
  else if (opts.command === "check") await runCheck(opts)
  else await runReport(opts)
}

function isMainModule() {
  const currentScript = process.argv?.[1]
  return !!currentScript && resolve(currentScript) === SCRIPT_PATH
}

if (isMainModule()) {
  main().catch(err => {
    console.error(err)
    process.exit(1)
  })
}
