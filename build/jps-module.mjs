#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {existsSync, readFileSync, writeFileSync} from "node:fs"
import {dirname, isAbsolute, relative, resolve, sep} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

export const usageExitCode = 2
export const checkFailedExitCode = 1
export const commandExitCode = 3

const scriptPath = fileURLToPath(import.meta.url)
const repoRoot = detectDefaultRoot()

export class UsageError extends Error {
  constructor(message) {
    super(message)
    this.name = "UsageError"
  }
}

export function isMainModule() {
  const currentScript = process["argv"]?.[1]
  return currentScript !== undefined && resolve(currentScript) === scriptPath
}

export function printUsage() {
  return [
    "Usage:",
    "  bun build/jps-module.mjs register <path-to-iml>... [--fix-iml-eof]",
    "  bun build/jps-module.mjs check <path-to-iml>... [--fix-iml-eof]",
    "",
    "Description:",
    "  Register JPS .iml modules in .idea/modules.xml and, for community modules,",
    "  community/.idea/modules.xml. Entries are ordered by the .iml basename without",
    "  the .iml suffix, matching org.jetbrains.intellij.build.ModulesXml.",
    "",
    "Options:",
    "  --fix-iml-eof  Remove trailing line breaks from listed .iml files",
    "  --help         Print this help",
  ].join("\n")
}

function createDefaultIo() {
  return {
    stdout: (message) => process.stdout.write(`${message}\n`),
    stderr: (message) => process.stderr.write(`${message}\n`),
  }
}

export function detectDefaultRoot(cwd = process.cwd(), exists = existsSync) {
  let current = resolve(cwd)
  while (true) {
    if (exists(resolve(current, ".idea/modules.xml"))) {
      const parent = dirname(current)
      if (current.endsWith(`${sep}community`) && resolve(parent, "community") === current && exists(resolve(parent, ".idea/modules.xml"))) {
        return parent
      }
      return current
    }

    const parent = dirname(current)
    if (parent === current) {
      return resolve(cwd)
    }
    current = parent
  }
}

export function createDefaultRuntime(rootDir = repoRoot) {
  return {
    rootDir,
    cwd: resolve(process.cwd()),
    exists: existsSync,
    readFile: (path) => readFileSync(path, "utf8"),
    writeFile: (path, content) => writeFileSync(path, content),
  }
}

export function parseArguments(argv) {
  let command = null
  let fixImlEof = false
  let help = false
  const imlPaths = []

  for (const arg of argv) {
    if (arg === "--help" || arg === "-h") {
      help = true
      continue
    }
    if (arg === "--fix-iml-eof") {
      fixImlEof = true
      continue
    }
    if (arg.startsWith("--")) {
      throw new UsageError(`Unknown option: ${arg}`)
    }
    if (command === null) {
      command = arg
      continue
    }
    imlPaths.push(arg)
  }

  if (help) {
    return {help, command, imlPaths, fixImlEof}
  }
  if (command !== "register" && command !== "check") {
    throw new UsageError("Command must be 'register' or 'check'.")
  }
  if (imlPaths.length === 0) {
    throw new UsageError("At least one .iml path is required.")
  }
  for (const imlPath of imlPaths) {
    if (!imlPath.endsWith(".iml")) {
      throw new UsageError(`Expected an .iml path, got: ${imlPath}`)
    }
  }

  return {help, command, imlPaths, fixImlEof}
}

function toPosixPath(path) {
  return path.split(sep).join("/")
}

function isInsidePath(path, parent) {
  const rel = relative(parent, path)
  return rel !== "" && !rel.startsWith("..") && !isAbsolute(rel)
}

function resolveImlPath(rawPath, runtime) {
  const pathBase = runtime.cwd ?? runtime.rootDir
  const absolutePath = isAbsolute(rawPath) ? resolve(rawPath) : resolve(pathBase, rawPath)
  if (!isInsidePath(absolutePath, runtime.rootDir)) {
    throw new Error(`Refusing to operate on a module outside the repository: ${rawPath}`)
  }
  if (!runtime.exists(absolutePath)) {
    throw new Error(`Module file does not exist: ${toPosixPath(relative(runtime.rootDir, absolutePath))}`)
  }
  return absolutePath
}

function moduleKeyFromProjectPath(projectRelativePath) {
  const fileName = projectRelativePath.substring(projectRelativePath.lastIndexOf("/") + 1)
  return fileName.endsWith(".iml") ? fileName.slice(0, -4) : fileName
}

function compareModuleKeys(left, right) {
  if (left < right) {
    return -1
  }
  if (left > right) {
    return 1
  }
  return 0
}

function createModuleFilePath(projectHome, modulePath) {
  const rel = relative(projectHome, modulePath)
  if (rel === "" || rel.startsWith("..") || isAbsolute(rel)) {
    throw new Error(`Module '${modulePath}' is not under project '${projectHome}'.`)
  }
  return `$PROJECT_DIR$/${toPosixPath(rel)}`
}

function parseModulesXml(text, modulesXmlPath) {
  const lineEnding = text.includes("\r\n") ? "\r\n" : "\n"
  const blockMatch = text.match(/(^[ \t]*<modules>\r?\n)([\s\S]*?)(^[ \t]*<\/modules>)/m)
  if (blockMatch === null) {
    throw new Error(`${modulesXmlPath}: missing <modules> block.`)
  }

  const [blockText, openLine, body, closeLine] = blockMatch
  const blockStart = blockMatch.index ?? 0
  const blockEnd = blockStart + blockText.length
  const entryPattern = /^([ \t]*)<module\s+fileurl="([^"]+)"\s+filepath="([^"]+)"\s*\/>[ \t]*(?:\r?\n|$)/gm
  const entries = []
  let match
  while ((match = entryPattern.exec(body)) !== null) {
    entries.push({
      indent: match[1],
      fileurl: match[2],
      filepath: match[3],
      originalIndex: entries.length,
    })
  }

  if (body.replace(entryPattern, "").trim().length > 0) {
    throw new Error(`${modulesXmlPath}: unexpected content inside <modules> block.`)
  }

  const openIndent = openLine.match(/^([ \t]*)/)?.[1] ?? ""
  const entryIndent = entries[0]?.indent ?? `${openIndent}  `
  return {blockStart, blockEnd, openLine, closeLine, entries, entryIndent, lineEnding}
}

function renderModuleEntry(entry, indent) {
  return `${indent}<module fileurl="file://${entry.filepath}" filepath="${entry.filepath}" />`
}

function normalizeFileUrl(filepath) {
  return `file://${filepath}`
}

function isProjectDirFilepathInsideProject(filepath, projectHome) {
  const prefix = "$PROJECT_DIR$/"
  if (!filepath.startsWith(prefix)) {
    return true
  }
  const absolutePath = resolve(projectHome, filepath.slice(prefix.length))
  return absolutePath === projectHome || isInsidePath(absolutePath, projectHome)
}

export function updateModulesXmlContent(text, projectHome, modulesXmlPath, modulePaths, options = {}) {
  const parsed = parseModulesXml(text, modulesXmlPath)
  const dropEntriesOutsideProjectHome = options.dropEntriesOutsideProjectHome ?? false
  const diagnostics = []
  const entryByFilepath = new Map()
  const keptEntries = []

  for (const entry of parsed.entries) {
    if (dropEntriesOutsideProjectHome && !isProjectDirFilepathInsideProject(entry.filepath, projectHome)) {
      diagnostics.push(`removed entry outside project for ${entry.filepath}`)
      continue
    }
    if (entryByFilepath.has(entry.filepath)) {
      diagnostics.push(`removed duplicate entry for ${entry.filepath}`)
      continue
    }
    const normalizedEntry = {
      filepath: entry.filepath,
      fileurl: normalizeFileUrl(entry.filepath),
      originalIndex: entry.originalIndex,
    }
    if (entry.fileurl !== normalizedEntry.fileurl) {
      diagnostics.push(`fixed fileurl for ${entry.filepath}`)
    }
    entryByFilepath.set(entry.filepath, normalizedEntry)
    keptEntries.push(normalizedEntry)
  }

  let nextOriginalIndex = parsed.entries.length
  for (const modulePath of modulePaths) {
    const filepath = createModuleFilePath(projectHome, modulePath)
    if (entryByFilepath.has(filepath)) {
      continue
    }
    const entry = {
      filepath,
      fileurl: normalizeFileUrl(filepath),
      originalIndex: nextOriginalIndex++,
    }
    diagnostics.push(`added entry for ${filepath}`)
    entryByFilepath.set(filepath, entry)
    keptEntries.push(entry)
  }

  const sortedEntries = [...keptEntries].sort((left, right) => {
    const byName = compareModuleKeys(moduleKeyFromProjectPath(left.filepath), moduleKeyFromProjectPath(right.filepath))
    return byName !== 0 ? byName : left.originalIndex - right.originalIndex
  })

  const beforeOrder = keptEntries.map((entry) => entry.filepath).join("\n")
  const afterOrder = sortedEntries.map((entry) => entry.filepath).join("\n")
  if (beforeOrder !== afterOrder) {
    diagnostics.push("reordered module entries")
  }

  const body = sortedEntries.length === 0
    ? ""
    : sortedEntries.map((entry) => renderModuleEntry(entry, parsed.entryIndent)).join(parsed.lineEnding) + parsed.lineEnding
  const updatedText = text.slice(0, parsed.blockStart) + parsed.openLine + body + parsed.closeLine + text.slice(parsed.blockEnd)

  return {
    changed: updatedText !== text,
    content: updatedText,
    diagnostics,
  }
}

function trimImlTrailingLineBreaks(content) {
  return content.replace(/[\r\n]+$/, "")
}

function formatRel(path, rootDir) {
  return toPosixPath(relative(rootDir, path))
}

function createProjectUpdates(imlPaths, runtime) {
  const rootDir = resolve(runtime.rootDir)
  const communityHome = resolve(rootDir, "community")
  const rootModules = []
  const communityModules = []

  for (const imlPath of imlPaths) {
    rootModules.push(imlPath)
    if (isInsidePath(imlPath, communityHome)) {
      communityModules.push(imlPath)
    }
  }

  const updates = [
    {
      projectHome: rootDir,
      modulesXmlPath: resolve(rootDir, ".idea/modules.xml"),
      modulePaths: rootModules,
      dropEntriesOutsideProjectHome: false,
    },
  ]
  if (communityModules.length > 0) {
    updates.push({
      projectHome: communityHome,
      modulesXmlPath: resolve(communityHome, ".idea/modules.xml"),
      modulePaths: communityModules,
      dropEntriesOutsideProjectHome: true,
    })
  }
  return updates
}

export function runOperation(args, runtime = createDefaultRuntime(), io = createDefaultIo()) {
  const imlPaths = args.imlPaths.map((imlPath) => resolveImlPath(imlPath, runtime))
  const write = args.command === "register"
  const changedFiles = []

  for (const update of createProjectUpdates(imlPaths, runtime)) {
    if (!runtime.exists(update.modulesXmlPath)) {
      throw new Error(`Project modules file does not exist: ${formatRel(update.modulesXmlPath, runtime.rootDir)}`)
    }
    const currentContent = runtime.readFile(update.modulesXmlPath)
    const result = updateModulesXmlContent(
      currentContent,
      update.projectHome,
      formatRel(update.modulesXmlPath, runtime.rootDir),
      update.modulePaths,
      {dropEntriesOutsideProjectHome: update.dropEntriesOutsideProjectHome},
    )
    if (!result.changed) {
      continue
    }
    changedFiles.push(formatRel(update.modulesXmlPath, runtime.rootDir))
    if (write) {
      runtime.writeFile(update.modulesXmlPath, result.content)
    }
    io.stdout(`${write ? "Updated" : "Would update"} ${formatRel(update.modulesXmlPath, runtime.rootDir)}: ${result.diagnostics.join(", ")}`)
  }

  if (args.fixImlEof) {
    for (const imlPath of imlPaths) {
      const currentContent = runtime.readFile(imlPath)
      const updatedContent = trimImlTrailingLineBreaks(currentContent)
      if (updatedContent === currentContent) {
        continue
      }
      changedFiles.push(formatRel(imlPath, runtime.rootDir))
      if (write) {
        runtime.writeFile(imlPath, updatedContent)
      }
      io.stdout(`${write ? "Fixed" : "Would fix"} ${formatRel(imlPath, runtime.rootDir)} trailing line break`)
    }
  }

  if (changedFiles.length === 0) {
    io.stdout("JPS module registration is already canonical.")
    return 0
  }
  if (!write) {
    io.stderr(`JPS module registration is not canonical: ${changedFiles.join(", ")}`)
    return checkFailedExitCode
  }
  return 0
}

export async function runCli(argv = process.argv.slice(2), {runtime = createDefaultRuntime(), io = createDefaultIo()} = {}) {
  let args
  try {
    args = parseArguments(argv)
  }
  catch (error) {
    if (error instanceof UsageError) {
      io.stderr(error.message)
      io.stderr("")
      io.stderr(printUsage())
      return usageExitCode
    }
    io.stderr(error instanceof Error ? error.message : String(error))
    return commandExitCode
  }

  if (args.help) {
    io.stdout(printUsage())
    return 0
  }

  try {
    return runOperation(args, runtime, io)
  }
  catch (error) {
    io.stderr(error instanceof Error ? `Error: ${error.message}` : `Error: ${String(error)}`)
    return commandExitCode
  }
}

if (isMainModule()) {
  const exitCode = await runCli()
  process.exit(exitCode)
}
