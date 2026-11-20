#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {existsSync, readFileSync, writeFileSync} from "node:fs"
import {dirname, join, relative, resolve} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

export const checkFailedExitCode = 1
export const usageExitCode = 2

export const beginMarker = "# BEGIN rust-tools: generated from community/build/rust-tools/lints.toml by sync.mjs. Edit that file."
export const endMarker = "# END rust-tools"

/** The files that each workspace directory carries as a copy: the source here, with `copyHeader` as the first line. */
export const configFiles = ["rustfmt.toml", "clippy.toml"]

/**
 * The manifests that carry a copy of the tables, relative to the ultimate root. The directory of each manifest gets a
 * copy of every file of `configFiles`.
 */
export const manifestPaths = [
  "community/build/dev-dist-tools/Cargo.toml",
  "build/dev-dist-tools/Cargo.toml",
  "community/tools/bt/Cargo.toml",
  "community/tools/vm/Cargo.toml",
  "docs/ij-void/portal-server/Cargo.toml",
  "plugins/air/native/Cargo.toml",
]

/**
 * The copies that a workspace of `manifestPaths` does not carry yet, by manifest: the names of `configFiles` in
 * `skip`, and the reason. The script writes the tables and every other copy of such a workspace, and names each
 * skipped copy with the reason. A skipped copy that exists is left as it is.
 */
export const skippedCopies = {}

/**
 * The added clippy sources of a workspace of `manifestPaths`, by manifest. Each is a TOML file here with a
 * `disallowed-methods` array of its own and only comments outside it. The copy of `clippy.toml` of such a workspace
 * gets those entries after the entries of `clippy.toml`. `.bazelrc` gives Bazel only `clippy.toml`, so each crate of
 * such a workspace gives its copy to rules_rust as `clippy_config`. A Bazel test of the workspace then compares the
 * copy with the text of `--render`.
 */
export const clippyAdditions = {
  "plugins/air/native/Cargo.toml": ["clippy-preserve-order.toml"],
}

const scriptPath = fileURLToPath(import.meta.url)
const sharedDir = dirname(scriptPath)

export function isMainModule() {
  const currentScript = process["argv"]?.[1]
  return currentScript !== undefined && resolve(currentScript) === scriptPath
}

export function printUsage() {
  return [
    "Usage:",
    "  bun community/build/rust-tools/sync.mjs [--check]",
    "  bun community/build/rust-tools/sync.mjs --render <manifest> <file> <out>",
    "",
    "Description:",
    "  Copies the [workspace.lints] tables of community/build/rust-tools/lints.toml into every",
    "  workspace Cargo.toml that carries the rust-tools markers, and copies rustfmt.toml and",
    "  clippy.toml into the directory of each such Cargo.toml. The clippy.toml of a workspace",
    "  that clippyAdditions names also gets the bans of its added sources. A manifest outside",
    "  this checkout (a community-only checkout has no ultimate paths) is skipped with a note.",
    "",
    "Options:",
    "  --check   Exit 1 and name each copy that differs or is missing; write nothing",
    "  --render  Write the copy of <file> for the workspace of <manifest> to <out>, and nothing",
    "            else. <manifest> is a path of manifestPaths, such as plugins/air/native/Cargo.toml.",
    "            A Bazel test compares a copy with this text.",
    "  --help    Print this help",
  ].join("\n")
}

/**
 * The two checkout layouts. `community` is the directory that holds `build/rust-tools`. `ultimate` is its parent
 * when that parent is the ultimate root, otherwise null.
 */
export function detectRoots(communityDir = resolve(sharedDir, "../.."), exists = existsSync) {
  const parent = resolve(communityDir, "..")
  const isUltimate =
    exists(join(parent, "MODULE.bazel")) && exists(join(parent, "bazel.cmd")) && resolve(parent, "community") === communityDir
  return {community: communityDir, ultimate: isUltimate ? parent : null}
}

/** Resolves `manifestPaths` against the roots. A path of the ultimate root resolves to null in a community checkout. */
export function resolveManifests(roots) {
  return manifestPaths.map((path) => {
    const communityPrefix = "community/"
    if (path.startsWith(communityPrefix)) {
      return {path, file: join(roots.community, path.slice(communityPrefix.length))}
    }
    return {path, file: roots.ultimate === null ? null : join(roots.ultimate, path)}
  })
}

/** The block that replaces the text between the markers: the markers and the lint tables. */
export function renderBlock(lintsToml) {
  const tables = lintsToml.split("\n").filter((line, index, lines) => {
    // Drop the file header: every line before the first table.
    const firstTable = lines.findIndex((candidate) => candidate.startsWith("["))
    return index >= firstTable
  })
  return [beginMarker, ...trimBlankEdges(tables), endMarker].join("\n")
}

function trimBlankEdges(lines) {
  let start = 0
  let end = lines.length
  while (start < end && lines[start].trim() === "") start++
  while (end > start && lines[end - 1].trim() === "") end--
  return lines.slice(start, end)
}

/** Replaces the lines from the begin marker to the end marker, both included, with `block`. */
export function applyBlock(manifest, block) {
  const lines = manifest.split("\n")
  const begin = lines.indexOf(beginMarker)
  const end = lines.indexOf(endMarker)
  if (begin === -1 || end === -1 || end < begin) {
    throw new Error(`the manifest has no rust-tools markers; add the lines\n${beginMarker}\n${endMarker}\nwhere the tables belong`)
  }
  return [...lines.slice(0, begin), ...block.split("\n"), ...lines.slice(end + 1)].join("\n")
}

/**
 * The first line of a copy of `file` with the added sources `additions`. Both files are TOML, so the line is a
 * comment.
 */
export function copyHeader(file, additions = []) {
  if (additions.length === 0) {
    return `# Generated from community/build/rust-tools/${file} by sync.mjs. Edit that file.`
  }
  const names = [file, ...additions]
  return `# Generated from community/build/rust-tools/${names.slice(0, -1).join(", ")} and ${names.at(-1)} by sync.mjs. Edit those files.`
}

/**
 * The copy of `file` in a workspace directory: the header line, then the source bytes. Each added source of
 * `additions`, `{name, text}`, gives its `disallowed-methods` entries after the entries of the source. An added source
 * with a line outside its array that is not blank and not a comment is refused, because the copy does not take that
 * line.
 */
export function renderCopy(file, source, additions = []) {
  const header = copyHeader(file, additions.map(({name}) => name))
  if (additions.length === 0) {
    return `${header}\n${source}`
  }
  const target = disallowedMethods(file, source)
  const added = additions.flatMap(({name, text}) => {
    const addition = disallowedMethods(name, text)
    const outside = addition.lines.find(
      (line, index) => (index < addition.begin || index > addition.end) && line.trim() !== "" && !line.trimStart().startsWith("#"),
    )
    if (outside !== undefined) {
      throw new Error(`${name} has the line \`${outside}\` outside its \`${disallowedMethodsLine}\` array, and a copy takes only the array`)
    }
    return addition.lines.slice(addition.begin + 1, addition.end)
  })
  return [header, ...target.lines.slice(0, target.end), ...added, ...target.lines.slice(target.end)].join("\n")
}

/**
 * The copy of `name` of `configFiles` in the directory of the manifest `path` of `manifestPaths`. `readSource` gives
 * the text of a source here by its file name.
 */
export function workspaceCopy(path, name, readSource, additionsByManifest = clippyAdditions) {
  const additions = name === "clippy.toml" ? (additionsByManifest[path] ?? []) : []
  return renderCopy(name, readSource(name), additions.map((addition) => ({name: addition, text: readSource(addition)})))
}

const disallowedMethodsLine = "disallowed-methods = ["

/** The lines of a clippy source, its line `disallowed-methods = [`, and the line `]` that closes the array after it. */
function disallowedMethods(name, text) {
  const lines = text.split("\n")
  const begin = lines.indexOf(disallowedMethodsLine)
  const end = begin === -1 ? -1 : lines.indexOf("]", begin + 1)
  if (end === -1) {
    throw new Error(`${name} has no line \`${disallowedMethodsLine}\` with a line \`]\` after it`)
  }
  return {lines, begin, end}
}

function createDefaultIo() {
  return {
    stdout: (message) => process.stdout.write(`${message}\n`),
    stderr: (message) => process.stderr.write(`${message}\n`),
  }
}

export function runCli(argv = process.argv.slice(2), options = {}) {
  const io = options.io ?? createDefaultIo()
  const roots = options.roots ?? detectRoots()
  const sourceDir = options.sourceDir ?? sharedDir
  const readText = options.readText ?? ((file) => readFileSync(file, "utf8"))
  const writeText = options.writeText ?? ((file, text) => writeFileSync(file, text, "utf8"))
  const exists = options.exists ?? existsSync
  const skips = options.skippedCopies ?? skippedCopies
  const additionsByManifest = options.clippyAdditions ?? clippyAdditions

  const readSource = (name) => readText(join(sourceDir, name))

  if (argv[0] === "--render") {
    const [path, name, out, ...rest] = argv.slice(1)
    if (out === undefined || rest.length > 0 || !manifestPaths.includes(path) || !configFiles.includes(name)) {
      io.stderr(`--render takes a path of manifestPaths, a file of configFiles and the output file: ${argv.slice(1).join(" ")}`)
      io.stderr(printUsage())
      return usageExitCode
    }
    writeText(out, workspaceCopy(path, name, readSource, additionsByManifest))
    return 0
  }

  const flags = new Set(argv)
  if (flags.has("--help")) {
    io.stdout(printUsage())
    return 0
  }
  const check = flags.delete("--check")
  if (flags.size > 0) {
    io.stderr(`unknown argument: ${[...flags].join(" ")}`)
    io.stderr(printUsage())
    return usageExitCode
  }

  const block = renderBlock(readSource("lints.toml"))
  const displayRoot = roots.ultimate ?? roots.community
  let differing = 0
  const sync = (file, current, updated, problem) => {
    if (updated === current) {
      return
    }
    if (check) {
      io.stderr(`${relative(displayRoot, file)} ${current === null ? "is missing" : problem}`)
      differing++
    } else {
      writeText(file, updated)
      io.stdout(`updated ${relative(displayRoot, file)}`)
    }
  }
  for (const {path, file} of resolveManifests(roots)) {
    if (file === null || !exists(file)) {
      io.stderr(`skipped ${path}: not in this checkout`)
      continue
    }
    const manifest = readText(file)
    sync(file, manifest, applyBlock(manifest, block), "differs from lints.toml")
    const skipped = skips[path]
    for (const name of configFiles) {
      const copy = join(dirname(file), name)
      if (skipped?.skip.includes(name)) {
        io.stderr(`skipped ${relative(displayRoot, copy)}: ${skipped.reason}`)
        continue
      }
      sync(copy, exists(copy) ? readText(copy) : null, workspaceCopy(path, name, readSource, additionsByManifest), `differs from ${name}`)
    }
  }
  if (differing > 0) {
    io.stderr(`${differing} file(s) differ. Run bun community/build/rust-tools/sync.mjs`)
    return checkFailedExitCode
  }
  return 0
}

if (isMainModule()) {
  process.exit(runCli())
}
