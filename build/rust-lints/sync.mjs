#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {existsSync, readFileSync, writeFileSync} from "node:fs"
import {dirname, join, relative, resolve} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

export const checkFailedExitCode = 1
export const usageExitCode = 2

export const beginMarker = "# BEGIN rust-lints: generated from community/build/rust-lints/lints.toml by sync.mjs. Edit that file."
export const endMarker = "# END rust-lints"

/** The manifests that carry a copy of the tables, relative to the ultimate root. */
export const manifestPaths = [
  "community/build/dev-dist-tools/Cargo.toml",
  "build/dev-dist-tools/Cargo.toml",
  "community/tools/bt/Cargo.toml",
  "plugins/air/tests/integration/vm-lane/Cargo.toml",
]

const scriptPath = fileURLToPath(import.meta.url)
const sharedDir = dirname(scriptPath)

export function isMainModule() {
  const currentScript = process["argv"]?.[1]
  return currentScript !== undefined && resolve(currentScript) === scriptPath
}

export function printUsage() {
  return [
    "Usage:",
    "  bun community/build/rust-lints/sync.mjs [--check]",
    "",
    "Description:",
    "  Copies the [workspace.lints] tables of community/build/rust-lints/lints.toml into every",
    "  workspace Cargo.toml that carries the rust-lints markers. A manifest outside this checkout",
    "  (a community-only checkout has no ultimate paths) is skipped with a note.",
    "",
    "Options:",
    "  --check  Exit 1 and name each copy that differs; write nothing",
    "  --help   Print this help",
  ].join("\n")
}

/**
 * The two checkout layouts. `community` is the directory that holds `build/rust-lints`. `ultimate` is its parent
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
    throw new Error(`the manifest has no rust-lints markers; add the lines\n${beginMarker}\n${endMarker}\nwhere the tables belong`)
  }
  return [...lines.slice(0, begin), ...block.split("\n"), ...lines.slice(end + 1)].join("\n")
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
  const lintsPath = options.lintsPath ?? join(sharedDir, "lints.toml")
  const readText = options.readText ?? ((file) => readFileSync(file, "utf8"))
  const writeText = options.writeText ?? ((file, text) => writeFileSync(file, text, "utf8"))
  const exists = options.exists ?? existsSync

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

  const block = renderBlock(readText(lintsPath))
  const displayRoot = roots.ultimate ?? roots.community
  let differing = 0
  for (const {path, file} of resolveManifests(roots)) {
    if (file === null || !exists(file)) {
      io.stderr(`skipped ${path}: not in this checkout`)
      continue
    }
    const current = readText(file)
    const updated = applyBlock(current, block)
    if (updated === current) {
      continue
    }
    if (check) {
      io.stderr(`${relative(displayRoot, file)} differs from lints.toml`)
      differing++
    } else {
      writeText(file, updated)
      io.stdout(`updated ${relative(displayRoot, file)}`)
    }
  }
  if (differing > 0) {
    io.stderr(`${differing} manifest(s) differ. Run bun community/build/rust-lints/sync.mjs`)
    return checkFailedExitCode
  }
  return 0
}

if (isMainModule()) {
  process.exit(runCli())
}
