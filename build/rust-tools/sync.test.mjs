// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, match, throws} from "node:assert/strict"
import {existsSync, mkdirSync, readFileSync, writeFileSync} from "node:fs"
import {mkdtemp, rm} from "node:fs/promises"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import {describe, it} from "node:test"
import {
  applyBlock,
  beginMarker,
  checkFailedExitCode,
  configFiles,
  copyHeader,
  detectRoots,
  endMarker,
  manifestPaths,
  optedOutManifests,
  renderBlock,
  renderCopy,
  resolveManifests,
  runCli,
  usageExitCode,
} from "./sync.mjs"

const lintsToml = [
  "# The policy header.",
  "# Two lines.",
  "",
  "[workspace.lints.rust]",
  "unsafe_op_in_unsafe_fn = \"deny\"",
  "",
  "[workspace.lints.clippy]",
  "pedantic = { level = \"warn\", priority = -1 }",
  "",
].join("\n")

const block = renderBlock(lintsToml)

const sources = {
  "rustfmt.toml": "# The format.\nmax_width = 140\n",
  "clippy.toml": "# The bans.\ndisallowed-methods = []\n",
}

function manifestWith(inner) {
  const head = ["[workspace]", "members = [\"crates/*\"]", ""]
  const tail = ["", "[workspace.dependencies]", "anyhow = \"1\"", ""]
  return [...head, beginMarker, ...inner, endMarker, ...tail].join("\n")
}

function writeFile(file, text) {
  mkdirSync(dirname(file), {recursive: true})
  writeFileSync(file, text, "utf8")
}

function createIo() {
  const out = []
  const err = []
  return {io: {stdout: (m) => out.push(m), stderr: (m) => err.push(m)}, out, err}
}

describe("renderBlock", () => {
  it("keeps the tables and drops the file header", () => {
    const lines = block.split("\n")
    equal(lines[0], beginMarker)
    equal(lines[1], "[workspace.lints.rust]")
    equal(lines.at(-1), endMarker)
    equal(lines.includes("# The policy header."), false)
  })
})

describe("applyBlock", () => {
  it("replaces the text between the markers, markers included", () => {
    const updated = applyBlock(manifestWith(["[workspace.lints.rust]", "old = \"allow\""]), block)
    equal(updated, manifestWith(block.split("\n").slice(1, -1)))
  })

  it("is idempotent", () => {
    const once = applyBlock(manifestWith([]), block)
    equal(applyBlock(once, block), once)
  })

  it("refuses a manifest without markers", () => {
    throws(() => applyBlock("[workspace]\n", block), /no rust-tools markers/)
  })
})

describe("renderCopy", () => {
  it("puts the header line before the source bytes", () => {
    equal(renderCopy("rustfmt.toml", sources["rustfmt.toml"]), `${copyHeader("rustfmt.toml")}\n# The format.\nmax_width = 140\n`)
    match(copyHeader("clippy.toml"), /^# Generated from community\/build\/rust-tools\/clippy.toml by sync.mjs/)
  })

  it("covers the format and the clippy configuration", () => {
    deepEqual(configFiles, ["rustfmt.toml", "clippy.toml"])
  })
})

describe("optedOutManifests", () => {
  it("names a manifest that manifestPaths does not list", () => {
    for (const path of Object.keys(optedOutManifests)) {
      equal(manifestPaths.includes(path), false)
    }
  })
})

describe("detectRoots and resolveManifests", () => {
  it("names the ultimate root when the community directory is its `community/`", () => {
    const files = new Set(["/repo/MODULE.bazel", "/repo/bazel.cmd"])
    deepEqual(detectRoots("/repo/community", (file) => files.has(file)), {community: "/repo/community", ultimate: "/repo"})
    const manifests = resolveManifests({community: "/repo/community", ultimate: "/repo"})
    equal(manifests[0].file, "/repo/community/build/dev-dist-tools/Cargo.toml")
    equal(manifests[1].file, "/repo/build/dev-dist-tools/Cargo.toml")
  })

  it("has no ultimate root in a community checkout", () => {
    deepEqual(detectRoots("/community", () => false), {community: "/community", ultimate: null})
    const manifests = resolveManifests({community: "/community", ultimate: null})
    equal(manifests[0].file, "/community/build/dev-dist-tools/Cargo.toml")
    equal(manifests[1].file, null)
  })
})

describe("runCli", () => {
  async function withCheckout(run) {
    const root = await mkdtemp(join(tmpdir(), "rust-tools-sync-test-"))
    try {
      const community = join(root, "community")
      writeFile(join(root, "MODULE.bazel"), "")
      writeFile(join(root, "bazel.cmd"), "")
      const sourceDir = join(community, "build/rust-tools")
      writeFile(join(sourceDir, "lints.toml"), lintsToml)
      for (const [name, text] of Object.entries(sources)) {
        writeFile(join(sourceDir, name), text)
      }
      // The community workspace has a stale table, a drifted rustfmt.toml and no clippy.toml. The ultimate one is in sync.
      const communityDir = join(community, "build/dev-dist-tools")
      const ultimateDir = join(root, "build/dev-dist-tools")
      const communityManifest = join(communityDir, "Cargo.toml")
      const ultimateManifest = join(ultimateDir, "Cargo.toml")
      writeFile(communityManifest, manifestWith(["stale = \"allow\""]))
      writeFile(join(communityDir, "rustfmt.toml"), "max_width = 100\n")
      writeFile(ultimateManifest, manifestWith(block.split("\n").slice(1, -1)))
      for (const [name, text] of Object.entries(sources)) {
        writeFile(join(ultimateDir, name), renderCopy(name, text))
      }
      const roots = {community, ultimate: root}
      await run({roots, sourceDir, communityDir, communityManifest, ultimateManifest})
    } finally {
      await rm(root, {recursive: true, force: true})
    }
  }

  it("check names each copy that differs or is missing and writes nothing", async () => {
    await withCheckout(({roots, sourceDir, communityDir, communityManifest}) => {
      const {io, err} = createIo()
      const before = readFileSync(communityManifest, "utf8")
      equal(runCli(["--check"], {io, roots, sourceDir}), checkFailedExitCode)
      equal(readFileSync(communityManifest, "utf8"), before)
      equal(readFileSync(join(communityDir, "rustfmt.toml"), "utf8"), "max_width = 100\n")
      equal(existsSync(join(communityDir, "clippy.toml")), false)
      const text = err.join("\n")
      match(text, /community\/build\/dev-dist-tools\/Cargo.toml differs from lints.toml/)
      match(text, /community\/build\/dev-dist-tools\/rustfmt.toml differs from rustfmt.toml/)
      match(text, /community\/build\/dev-dist-tools\/clippy.toml is missing/)
      match(text, /skipped plugins\/air\/tests\/integration\/vm-lane\/Cargo.toml: opted out/)
      match(text, /3 file\(s\) differ/)
      // The ultimate workspace is in sync, so no line names it.
      equal(err.some((line) => line.startsWith("build/")), false)
    })
  })

  it("write updates the differing copies only, then check is clean", async () => {
    await withCheckout(({roots, sourceDir, communityDir, communityManifest, ultimateManifest}) => {
      const {io, out} = createIo()
      equal(runCli([], {io, roots, sourceDir}), 0)
      deepEqual(out, [
        "updated community/build/dev-dist-tools/Cargo.toml",
        "updated community/build/dev-dist-tools/rustfmt.toml",
        "updated community/build/dev-dist-tools/clippy.toml",
      ])
      equal(readFileSync(communityManifest, "utf8"), manifestWith(block.split("\n").slice(1, -1)))
      for (const [name, text] of Object.entries(sources)) {
        equal(readFileSync(join(communityDir, name), "utf8"), renderCopy(name, text))
      }
      equal(existsSync(ultimateManifest), true)
      equal(runCli(["--check"], {io: createIo().io, roots, sourceDir}), 0)
    })
  })

  it("rejects an unknown argument", () => {
    const {io} = createIo()
    equal(runCli(["--fix"], {io, roots: {community: "/none", ultimate: null}, sourceDir: "/none"}), usageExitCode)
  })
})
