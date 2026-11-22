// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, match, throws} from "node:assert/strict"
import {existsSync, mkdirSync, readFileSync, writeFileSync} from "node:fs"
import {mkdtemp, rm} from "node:fs/promises"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import {describe, it} from "node:test"
import {fileURLToPath} from "node:url"
import {
  applyBlock,
  beginMarker,
  checkFailedExitCode,
  clippyAdditions,
  configFiles,
  copyHeader,
  detectRoots,
  endMarker,
  manifestPaths,
  renderBlock,
  renderCopy,
  resolveManifests,
  runCli,
  skippedCopies,
  usageExitCode,
  workspaceCopy,
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

const bans = "# The bans.\ndisallowed-methods = [\n  { path = \"std::f\" },\n]\n"

const sources = {
  "rustfmt.toml": "# The format.\nmax_width = 140\n",
  "clippy.toml": bans,
}

// An added clippy source of the community workspace, so that the tests cover one whatever `clippyAdditions` holds.
const added = {"more.toml": "# More.\ndisallowed-methods = [\n  { path = \"more::h\" },\n]\n"}
const communityAdditions = {"community/build/dist-tools/Cargo.toml": ["more.toml"]}

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

  it("puts the entries of each added source after the entries of the source, inside the array", () => {
    const additions = [
      {name: "a.toml", text: "# A.\ndisallowed-methods = [\n  # Why a.\n  { path = \"a::f\" },\n]\n"},
      {name: "b.toml", text: "disallowed-methods = [\n  { path = \"b::g\" },\n]\n"},
    ]
    equal(
      renderCopy("clippy.toml", bans, additions),
      [
        "# Generated from community/build/rust-tools/clippy.toml, a.toml and b.toml by sync.mjs. Edit those files.",
        "# The bans.",
        "disallowed-methods = [",
        "  { path = \"std::f\" },",
        "  # Why a.",
        "  { path = \"a::f\" },",
        "  { path = \"b::g\" },",
        "]",
        "",
      ].join("\n"),
    )
    equal(
      copyHeader("clippy.toml", ["a.toml"]),
      "# Generated from community/build/rust-tools/clippy.toml and a.toml by sync.mjs. Edit those files.",
    )
  })

  it("refuses an added source without the array lines", () => {
    const withoutLines = [{name: "a.toml", text: "disallowed-methods = []\n"}]
    throws(() => renderCopy("clippy.toml", bans, withoutLines), /a.toml has no line `disallowed-methods = \[`/)
  })

  it("refuses an added source with a key outside its array, and takes comments and blank lines there", () => {
    const withKey = "disallowed-methods = [\n  { path = \"a::f\" },\n]\n\ndisallowed-types = [\"a::T\"]\n"
    throws(
      () => renderCopy("clippy.toml", bans, [{name: "a.toml", text: withKey}]),
      /a.toml has the line `disallowed-types = \["a::T"\]` outside its/,
    )
    const withComments = "# A.\n\n  # Indented.\ndisallowed-methods = [\n  { path = \"a::f\" },\n]\n# After.\n"
    match(renderCopy("clippy.toml", bans, [{name: "a.toml", text: withComments}]), /std::f" },\n {2}\{ path = "a::f" },\n\]\n$/)
  })
})

describe("workspaceCopy", () => {
  const readSource = (name) => ({...sources, ...added})[name]

  it("gives the added sources of a manifest to its clippy.toml only", () => {
    const path = "community/build/dist-tools/Cargo.toml"
    equal(
      workspaceCopy(path, "clippy.toml", readSource, communityAdditions),
      renderCopy("clippy.toml", bans, [{name: "more.toml", text: added["more.toml"]}]),
    )
    equal(workspaceCopy(path, "rustfmt.toml", readSource, communityAdditions), renderCopy("rustfmt.toml", sources["rustfmt.toml"]))
    equal(workspaceCopy("community/tools/bt/Cargo.toml", "clippy.toml", readSource, communityAdditions), renderCopy("clippy.toml", bans))
  })
})

describe("clippyAdditions", () => {
  it("names a manifest of manifestPaths and a TOML source here with the array lines and only comments outside", () => {
    const sourceDir = dirname(fileURLToPath(import.meta.url))
    for (const [path, names] of Object.entries(clippyAdditions)) {
      equal(manifestPaths.includes(path), true)
      for (const name of names) {
        // `copy_sources` of `BUILD.bazel` gives Bazel every `*.toml` file here.
        match(name, /^[^/]+\.toml$/)
        const text = readFileSync(join(sourceDir, name), "utf8")
        match(renderCopy("clippy.toml", bans, [{name, text}]), /^# Generated from /)
      }
    }
  })
})

describe("skippedCopies", () => {
  it("names a manifest of manifestPaths and a file of configFiles, with a reason", () => {
    for (const [path, {skip, reason}] of Object.entries(skippedCopies)) {
      equal(manifestPaths.includes(path), true)
      equal(skip.length > 0, true)
      for (const name of skip) {
        equal(configFiles.includes(name), true)
      }
      match(reason, /\S/)
    }
  })
})

describe("detectRoots and resolveManifests", () => {
  it("names the ultimate root when the community directory is its `community/`", () => {
    const files = new Set(["/repo/MODULE.bazel", "/repo/bazel.cmd"])
    deepEqual(detectRoots("/repo/community", (file) => files.has(file)), {community: "/repo/community", ultimate: "/repo"})
    const manifests = resolveManifests({community: "/repo/community", ultimate: "/repo"})
    equal(manifests[0].file, "/repo/community/build/dist-tools/Cargo.toml")
    equal(manifests[1].file, "/repo/build/dist-tools/Cargo.toml")
  })

  it("has no ultimate root in a community checkout", () => {
    deepEqual(detectRoots("/community", () => false), {community: "/community", ultimate: null})
    const manifests = resolveManifests({community: "/community", ultimate: null})
    equal(manifests[0].file, "/community/build/dist-tools/Cargo.toml")
    equal(manifests[1].file, null)
  })
})

describe("runCli", () => {
  // A skip of the clippy.toml of the UI-lane controller, so that the tests cover a skipped copy whatever `skippedCopies` holds.
  const skips = {
    "community/tools/vm/Cargo.toml": {skip: ["clippy.toml"], reason: "the test keeps its own copy"},
  }

  async function withCheckout(run) {
    const root = await mkdtemp(join(tmpdir(), "rust-tools-sync-test-"))
    try {
      const community = join(root, "community")
      writeFile(join(root, "MODULE.bazel"), "")
      writeFile(join(root, "bazel.cmd"), "")
      const sourceDir = join(community, "build/rust-tools")
      writeFile(join(sourceDir, "lints.toml"), lintsToml)
      for (const [name, text] of Object.entries({...sources, ...added})) {
        writeFile(join(sourceDir, name), text)
      }
      // The community workspace has a stale table, a drifted rustfmt.toml and no clippy.toml. The ultimate one is in sync.
      // The controller workspace has the tables, no rustfmt.toml, and a clippy.toml of its own, which `skips` keeps.
      const communityDir = join(community, "build/dist-tools")
      const ultimateDir = join(root, "build/dist-tools")
      const vmDir = join(community, "tools/vm")
      const communityManifest = join(communityDir, "Cargo.toml")
      const ultimateManifest = join(ultimateDir, "Cargo.toml")
      writeFile(communityManifest, manifestWith(["stale = \"allow\""]))
      writeFile(join(communityDir, "rustfmt.toml"), "max_width = 100\n")
      writeFile(ultimateManifest, manifestWith(block.split("\n").slice(1, -1)))
      for (const [name, text] of Object.entries(sources)) {
        writeFile(join(ultimateDir, name), renderCopy(name, text))
      }
      writeFile(join(vmDir, "Cargo.toml"), manifestWith(block.split("\n").slice(1, -1)))
      writeFile(join(vmDir, "clippy.toml"), "# Its own.\n")
      const roots = {community, ultimate: root}
      await run({roots, sourceDir, skippedCopies: skips, communityDir, communityManifest, ultimateManifest, vmDir})
    } finally {
      await rm(root, {recursive: true, force: true})
    }
  }

  it("check names each copy that differs or is missing and writes nothing", async () => {
    await withCheckout(({roots, sourceDir, skippedCopies, communityDir, communityManifest, vmDir}) => {
      const {io, err} = createIo()
      const before = readFileSync(communityManifest, "utf8")
      equal(runCli(["--check"], {io, roots, sourceDir, skippedCopies}), checkFailedExitCode)
      equal(readFileSync(communityManifest, "utf8"), before)
      equal(readFileSync(join(communityDir, "rustfmt.toml"), "utf8"), "max_width = 100\n")
      equal(existsSync(join(communityDir, "clippy.toml")), false)
      const text = err.join("\n")
      match(text, /community\/build\/dist-tools\/Cargo.toml differs from lints.toml/)
      match(text, /community\/build\/dist-tools\/rustfmt.toml differs from rustfmt.toml/)
      match(text, /community\/build\/dist-tools\/clippy.toml is missing/)
      match(text, /community\/tools\/vm\/rustfmt.toml is missing/)
      match(text, /skipped community\/tools\/vm\/clippy.toml: the test keeps its own copy/)
      match(text, /skipped community\/tools\/bt\/Cargo.toml: not in this checkout/)
      match(text, /4 file\(s\) differ/)
      equal(existsSync(join(vmDir, "rustfmt.toml")), false)
      // The ultimate workspace is in sync, so no line names it.
      equal(err.some((line) => line.startsWith("build/")), false)
    })
  })

  it("write updates the differing copies only, then check is clean", async () => {
    await withCheckout(({roots, sourceDir, skippedCopies, communityDir, communityManifest, ultimateManifest, vmDir}) => {
      const {io, out} = createIo()
      equal(runCli([], {io, roots, sourceDir, skippedCopies}), 0)
      deepEqual(out, [
        "updated community/build/dist-tools/Cargo.toml",
        "updated community/build/dist-tools/rustfmt.toml",
        "updated community/build/dist-tools/clippy.toml",
        "updated community/tools/vm/rustfmt.toml",
      ])
      equal(readFileSync(join(vmDir, "rustfmt.toml"), "utf8"), renderCopy("rustfmt.toml", sources["rustfmt.toml"]))
      equal(readFileSync(join(vmDir, "clippy.toml"), "utf8"), "# Its own.\n")
      equal(readFileSync(communityManifest, "utf8"), manifestWith(block.split("\n").slice(1, -1)))
      for (const [name, text] of Object.entries(sources)) {
        equal(readFileSync(join(communityDir, name), "utf8"), renderCopy(name, text))
      }
      equal(existsSync(ultimateManifest), true)
      equal(runCli(["--check"], {io: createIo().io, roots, sourceDir, skippedCopies}), 0)
    })
  })

  it("write gives a workspace of clippyAdditions the bans of its added sources, then check is clean", async () => {
    await withCheckout(({roots, sourceDir, skippedCopies, communityDir}) => {
      const options = {roots, sourceDir, skippedCopies, clippyAdditions: communityAdditions}
      equal(runCli([], {io: createIo().io, ...options}), 0)
      const copy = readFileSync(join(communityDir, "clippy.toml"), "utf8")
      equal(copy, renderCopy("clippy.toml", bans, [{name: "more.toml", text: added["more.toml"]}]))
      match(copy, /std::f" },\n {2}\{ path = "more::h" },\n\]\n$/)
      equal(readFileSync(join(communityDir, "rustfmt.toml"), "utf8"), renderCopy("rustfmt.toml", sources["rustfmt.toml"]))
      equal(runCli(["--check"], {io: createIo().io, ...options}), 0)
      // Without the added source, the copy differs from `clippy.toml`.
      const {io, err} = createIo()
      equal(runCli(["--check"], {io, ...options, clippyAdditions: {}}), checkFailedExitCode)
      match(err.join("\n"), /community\/build\/dist-tools\/clippy.toml differs from clippy.toml/)
    })
  })

  it("render writes the copy that write gives the workspace, and writes nothing else", async () => {
    await withCheckout(async ({roots, sourceDir, skippedCopies, communityDir, communityManifest}) => {
      const options = {roots, sourceDir, skippedCopies, clippyAdditions: communityAdditions}
      const outDir = await mkdtemp(join(tmpdir(), "rust-tools-render-test-"))
      try {
        const out = join(outDir, "clippy.toml")
        const {io, out: printed, err} = createIo()
        equal(runCli(["--render", "community/build/dist-tools/Cargo.toml", "clippy.toml", out], {io, ...options}), 0)
        deepEqual([printed, err], [[], []])
        equal(existsSync(join(communityDir, "clippy.toml")), false)
        equal(readFileSync(communityManifest, "utf8"), manifestWith(["stale = \"allow\""]))
        equal(runCli([], {io: createIo().io, ...options}), 0)
        equal(readFileSync(out, "utf8"), readFileSync(join(communityDir, "clippy.toml"), "utf8"))
      } finally {
        await rm(outDir, {recursive: true, force: true})
      }
    })
  })

  it("render refuses a manifest outside manifestPaths, a file outside configFiles and a wrong count of arguments", () => {
    const options = {roots: {community: "/none", ultimate: null}, sourceDir: "/none"}
    for (const args of [
      ["other/Cargo.toml", "clippy.toml", "/none/out"],
      ["plugins/air/native/Cargo.toml", "lints.toml", "/none/out"],
      ["plugins/air/native/Cargo.toml", "clippy.toml"],
      ["plugins/air/native/Cargo.toml", "clippy.toml", "/none/out", "--check"],
    ]) {
      const {io, err} = createIo()
      equal(runCli(["--render", ...args], {io, ...options}), usageExitCode)
      match(err[0], /^--render takes a path of manifestPaths/)
    }
  })

  it("rejects an unknown argument", () => {
    const {io} = createIo()
    equal(runCli(["--fix"], {io, roots: {community: "/none", ultimate: null}, sourceDir: "/none"}), usageExitCode)
  })
})
