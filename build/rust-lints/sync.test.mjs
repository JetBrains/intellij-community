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
  detectRoots,
  endMarker,
  renderBlock,
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
    throws(() => applyBlock("[workspace]\n", block), /no rust-lints markers/)
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
    const root = await mkdtemp(join(tmpdir(), "rust-lints-sync-test-"))
    try {
      const community = join(root, "community")
      writeFile(join(root, "MODULE.bazel"), "")
      writeFile(join(root, "bazel.cmd"), "")
      const lintsPath = join(community, "build/rust-lints/lints.toml")
      writeFile(lintsPath, lintsToml)
      const communityManifest = join(community, "build/dev-dist-tools/Cargo.toml")
      const ultimateManifest = join(root, "build/dev-dist-tools/Cargo.toml")
      writeFile(communityManifest, manifestWith(["stale = \"allow\""]))
      writeFile(ultimateManifest, manifestWith(block.split("\n").slice(1, -1)))
      const roots = {community, ultimate: root}
      await run({roots, lintsPath, communityManifest, ultimateManifest})
    } finally {
      await rm(root, {recursive: true, force: true})
    }
  }

  it("check names the copy that differs and writes nothing", async () => {
    await withCheckout(({roots, lintsPath, communityManifest}) => {
      const {io, err} = createIo()
      const before = readFileSync(communityManifest, "utf8")
      equal(runCli(["--check"], {io, roots, lintsPath}), checkFailedExitCode)
      equal(readFileSync(communityManifest, "utf8"), before)
      match(err.join("\n"), /community\/build\/dev-dist-tools\/Cargo.toml differs/)
      match(err.join("\n"), /skipped plugins\/air/)
    })
  })

  it("write updates the differing copy only, then check is clean", async () => {
    await withCheckout(({roots, lintsPath, communityManifest, ultimateManifest}) => {
      const {io, out} = createIo()
      equal(runCli([], {io, roots, lintsPath}), 0)
      deepEqual(out, ["updated community/build/dev-dist-tools/Cargo.toml"])
      equal(readFileSync(communityManifest, "utf8"), manifestWith(block.split("\n").slice(1, -1)))
      equal(existsSync(ultimateManifest), true)
      equal(runCli(["--check"], {io: createIo().io, roots, lintsPath}), 0)
    })
  })

  it("rejects an unknown argument", () => {
    const {io} = createIo()
    equal(runCli(["--fix"], {io, roots: {community: "/none", ultimate: null}, lintsPath: "/none/lints.toml"}), usageExitCode)
  })
})
