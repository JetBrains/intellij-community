#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync} from "node:fs"
import {basename, dirname, isAbsolute, join, relative, resolve, sep} from "node:path"
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
    "  bun build/jps-module.mjs create <path-to-iml> [--kind <kind>] [--plugin <path-to-plugin-xml>]",
    "  bun build/jps-module.mjs register <path-to-iml>... [--fix-iml-eof]",
    "  bun build/jps-module.mjs unregister <path-to-iml>...",
    "  bun build/jps-module.mjs check <path-to-iml>... [--fix-iml-eof]",
    "",
    "Description:",
    "  Register JPS .iml modules in .idea/modules.xml and, for community modules,",
    "  community/.idea/modules.xml. Entries are ordered by the .iml basename without",
    "  the .iml suffix, matching org.jetbrains.intellij.build.ModulesXml.",
    "",
    "  `unregister` drops the entries of the listed modules. Use it for the old path of a",
    "  renamed or deleted module, which `register` keeps. The listed .iml file does not",
    "  have to exist. Only a listed module loses its entry, so a partial checkout keeps",
    "  the rest.",
    "",
    "  `create` creates a new module. The .iml path sets the module name and directory.",
    "  The `src` root gets the package prefix `com.<module name>`. The kind `empty` writes",
    "  a minimal module. The kinds `shared`, `frontend` and `backend` copy the template",
    "  plugins/.remdev/remDevFeatureSample.<kind>. The default kind is the suffix of the",
    "  module name when it is one of these kinds, else `empty`. The command registers the",
    "  module and adds it to the <content> of the plugin descriptor with the loading rule",
    "  of the kind. For `shared`, `frontend` and `backend`, the default descriptor is",
    "  ../resources/META-INF/plugin.xml beside the module directory. An `empty` module goes",
    "  into a plugin only with --plugin. After `create`, run the JPS to Bazel converter.",
    "",
    "Options:",
    "  --kind <kind>      Module kind for `create`: empty, shared, frontend or backend",
    "  --plugin <path>    Plugin descriptor that gets the new content module",
    "  --fix-iml-eof      Remove trailing line breaks from listed .iml files",
    "  --help             Print this help",
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
    writeFile: (path, content) => {
      mkdirSync(dirname(path), {recursive: true})
      writeFileSync(path, content)
    },
    listFiles: listFilesRecursively,
  }
}

/** Lists the files under `dir` as paths relative to it, with `/` as the separator. */
export function listFilesRecursively(dir) {
  return readdirSync(dir, {recursive: true, withFileTypes: true})
    .filter((entry) => entry.isFile())
    .map((entry) => toPosixPath(relative(dir, join(entry.parentPath ?? entry.path, entry.name))))
    .sort()
}

export const moduleKinds = ["empty", "shared", "frontend", "backend"]

const commands = ["create", "register", "unregister", "check"]

export function parseArguments(argv) {
  let command = null
  let fixImlEof = false
  let help = false
  let kind = null
  let pluginXmlPath = null
  const imlPaths = []

  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index]
    if (arg === "--help" || arg === "-h") {
      help = true
      continue
    }
    if (arg === "--fix-iml-eof") {
      fixImlEof = true
      continue
    }
    if (arg === "--kind" || arg === "--plugin") {
      const value = argv[++index]
      if (value === undefined || value.startsWith("--")) {
        throw new UsageError(`Option ${arg} needs a value.`)
      }
      if (arg === "--kind") {
        kind = value
      }
      else {
        pluginXmlPath = value
      }
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

  const result = {help, command, imlPaths, fixImlEof, kind, pluginXmlPath}
  if (help) {
    return result
  }
  if (!commands.includes(command)) {
    throw new UsageError("Command must be 'create', 'register', 'unregister' or 'check'.")
  }
  if (imlPaths.length === 0) {
    throw new UsageError("At least one .iml path is required.")
  }
  // `unregister` takes the path of a module that is already gone, so there is no file to fix.
  if (command === "unregister" && fixImlEof) {
    throw new UsageError("Option --fix-iml-eof does not apply to 'unregister'.")
  }
  if (command === "create") {
    if (imlPaths.length !== 1) {
      throw new UsageError("'create' takes exactly one .iml path.")
    }
    // `create` writes the .iml in the canonical form, so there is no file to fix.
    if (fixImlEof) {
      throw new UsageError("Option --fix-iml-eof does not apply to 'create'.")
    }
    if (kind !== null && !moduleKinds.includes(kind)) {
      throw new UsageError(`Unknown module kind '${kind}'. Expected one of: ${moduleKinds.join(", ")}.`)
    }
  }
  else if (kind !== null || pluginXmlPath !== null) {
    throw new UsageError("Options --kind and --plugin apply only to 'create'.")
  }
  for (const imlPath of imlPaths) {
    if (!imlPath.endsWith(".iml")) {
      throw new UsageError(`Expected an .iml path, got: ${imlPath}`)
    }
  }

  return result
}

function toPosixPath(path) {
  return path.split(sep).join("/")
}

function isInsidePath(path, parent) {
  const rel = relative(parent, path)
  return rel !== "" && !rel.startsWith("..") && !isAbsolute(rel)
}

function resolveImlPath(rawPath, runtime, requireExisting = true) {
  const pathBase = runtime.cwd ?? runtime.rootDir
  const absolutePath = isAbsolute(rawPath) ? resolve(rawPath) : resolve(pathBase, rawPath)
  if (!isInsidePath(absolutePath, runtime.rootDir)) {
    throw new Error(`Refusing to operate on a module outside the repository: ${rawPath}`)
  }
  if (requireExisting && !runtime.exists(absolutePath)) {
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
  // `remove` turns `modulePaths` from the entries to ensure into the entries to drop.
  const remove = options.remove ?? false
  const filepathsToRemove = new Set(remove ? modulePaths.map((modulePath) => createModuleFilePath(projectHome, modulePath)) : [])
  const diagnostics = []
  const entryByFilepath = new Map()
  const keptEntries = []

  for (const entry of parsed.entries) {
    if (filepathsToRemove.has(entry.filepath)) {
      diagnostics.push(`removed entry for ${entry.filepath}`)
      continue
    }
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
  for (const modulePath of remove ? [] : modulePaths) {
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
  const remove = args.command === "unregister"
  const imlPaths = args.imlPaths.map((imlPath) => resolveImlPath(imlPath, runtime, !remove))
  const write = args.command === "register" || remove
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
      {dropEntriesOutsideProjectHome: update.dropEntriesOutsideProjectHome, remove: remove},
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

const templateModulesDir = "plugins/.remdev"
const templateModuleName = "remDevFeatureSample"
// The JPS to Bazel converter generates `BUILD.bazel`, so a copy of the template file would only go stale.
const templateFilesToSkip = new Set(["BUILD.bazel"])

/** The loading rule of each module kind, as the attributes of its `<module>` entry in `<content>`. */
const contentModuleAttributes = {
  empty: "",
  shared: ` loading="required"`,
  frontend: ` required-if-available="intellij.platform.frontend"`,
  backend: ` required-if-available="intellij.platform.backend"`,
}

export function inferModuleKind(moduleName) {
  return moduleKinds.find((kind) => kind !== "empty" && moduleName.endsWith(`.${kind}`)) ?? "empty"
}

export function renderEmptyModuleIml(moduleName) {
  return [
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
    "<module type=\"JAVA_MODULE\" version=\"4\">",
    "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">",
    "    <exclude-output />",
    "    <content url=\"file://$MODULE_DIR$\">",
    "      <sourceFolder url=\"file://$MODULE_DIR$/resources\" type=\"java-resource\" />",
    `      <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" packagePrefix="com.${moduleName}" />`,
    "    </content>",
    "    <orderEntry type=\"inheritedJdk\" />",
    "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />",
    "    <orderEntry type=\"library\" name=\"kotlin-stdlib\" level=\"project\" />",
    "  </component>",
    "</module>",
  ].join("\n")
}

function shortModuleName(moduleName) {
  return moduleName.startsWith("intellij.") ? moduleName.slice("intellij.".length) : moduleName
}

/**
 * The name replacements that turn a template module into `moduleName`. The more specific name goes first,
 * so the short name never rewrites a part of the full name.
 */
export function templateReplacements(moduleName, kind) {
  const familyName = moduleName.slice(0, -`.${kind}`.length)
  const sharedModuleName = `${familyName}.shared`
  return [
    [`intellij.${templateModuleName}.${kind}`, moduleName],
    [`${templateModuleName}.${kind}`, shortModuleName(moduleName)],
    [`intellij.${templateModuleName}.shared`, sharedModuleName],
    [`${templateModuleName}.shared`, shortModuleName(sharedModuleName)],
  ]
}

function applyReplacements(text, replacements) {
  return replacements.reduce((current, [from, to]) => current.split(from).join(to), text)
}

/** The modules of the root project: the module name and the absolute module directory of each entry. */
function registeredModules(runtime) {
  const modulesXmlPath = resolve(runtime.rootDir, ".idea/modules.xml")
  if (!runtime.exists(modulesXmlPath)) {
    throw new Error(`Project modules file does not exist: ${formatRel(modulesXmlPath, runtime.rootDir)}`)
  }
  const parsed = parseModulesXml(runtime.readFile(modulesXmlPath), ".idea/modules.xml")
  const prefix = "$PROJECT_DIR$/"
  return parsed.entries.map((entry) => ({
    name: moduleKeyFromProjectPath(entry.filepath),
    dir: entry.filepath.startsWith(prefix) ? dirname(resolve(runtime.rootDir, entry.filepath.slice(prefix.length))) : null,
  }))
}

function registeredModuleNames(runtime) {
  return new Set(registeredModules(runtime).map((module) => module.name))
}

function planModuleFiles(imlPath, moduleName, kind, runtime) {
  const moduleDir = dirname(imlPath)
  if (kind === "empty") {
    const files = [
      {path: imlPath, content: renderEmptyModuleIml(moduleName)},
      {path: join(moduleDir, "resources", `${moduleName}.xml`), content: "<idea-plugin>\n</idea-plugin>\n"},
    ]
    // An empty `src` keeps a file, so that git keeps the source root.
    if (!runtime.exists(join(moduleDir, "src"))) {
      files.push({path: join(moduleDir, "src", ".keep"), content: ""})
    }
    return files
  }

  if (!moduleName.endsWith(`.${kind}`)) {
    throw new Error(`The name of a ${kind} module ends with '.${kind}': ${moduleName}`)
  }
  const templateDir = resolve(runtime.rootDir, templateModulesDir, `${templateModuleName}.${kind}`)
  if (!runtime.exists(templateDir)) {
    throw new Error(`Template of the ${kind} kind does not exist: ${formatRel(templateDir, runtime.rootDir)}. ` +
                    "Only the ultimate checkout has it. For a module without the template, pass --kind empty.")
  }
  const replacements = templateReplacements(moduleName, kind)
  if (kind !== "shared") {
    const sharedModuleName = replacements[2][1]
    if (!registeredModuleNames(runtime).has(sharedModuleName)) {
      throw new Error(`The ${kind} module depends on ${sharedModuleName}, which is not registered. Create the shared module first.`)
    }
  }
  return runtime.listFiles(templateDir)
    .filter((relativePath) => !templateFilesToSkip.has(basename(relativePath)))
    .map((relativePath) => {
      const path = join(moduleDir, applyReplacements(relativePath, replacements))
      const content = applyReplacements(runtime.readFile(join(templateDir, relativePath)), replacements)
      return {path, content: path.endsWith(".iml") ? trimImlTrailingLineBreaks(content) : content}
    })
}

// The direct children of <idea-plugin> that go after <content>. A new <content> block goes before the first of them.
const pluginXmlElementsAfterContent = /^([ \t]*)(?:<!-- region|<dependencies\b|<depends\b|<xi:include\b|<resource-bundle\b|<extensionPoints\b|<extensions\b|<actions\b|<applicationListeners\b|<projectListeners\b)/m

/**
 * Adds a `<module>` entry for `moduleName` to the first `<content>` block of a plugin descriptor.
 * A self-closing `<content/>` becomes a block. Without a block, a new `<content namespace="jetbrains">` block goes
 * before the dependencies and extensions of the plugin. A module that is in any `<content>` block already stays as it is.
 */
export function addContentModuleEntry(text, moduleName, kind, pluginXmlPath) {
  const lineEnding = text.includes("\r\n") ? "\r\n" : "\n"
  const entry = `<module name="${moduleName}"${contentModuleAttributes[kind]}/>`
  const existingEntry = new RegExp(`<module\\s+name="${moduleName.replaceAll(".", "\\.")}"`)
  // A self-closing `<content .../>` has no body, so the pattern skips it.
  for (const block of text.matchAll(/<content\b(?:[^>]*[^/>])?>([\s\S]*?)<\/content>/g)) {
    if (existingEntry.test(block[1])) {
      return {changed: false, content: text}
    }
  }
  const contentMatch = text.match(/^([ \t]*)<content\b([^>]*?)(\/?)>/m)
  if (contentMatch === null) {
    const anchor = text.match(pluginXmlElementsAfterContent) ?? text.match(/^([ \t]*)<\/idea-plugin>/m)
    if (anchor === null) {
      throw new Error(`${pluginXmlPath}: missing <idea-plugin> root.`)
    }
    const indent = anchor[0].startsWith("</idea-plugin>") ? "  " : anchor[1]
    const block = [
      `${indent}<content namespace="jetbrains">`,
      `${indent}  ${entry}`,
      `${indent}</content>`,
      "",
    ].join(lineEnding)
    const at = anchor.index
    return {changed: true, content: text.slice(0, at) + block + lineEnding + text.slice(at)}
  }

  const [openTag, indent, attributes, selfClosing] = contentMatch
  const openStart = contentMatch.index
  if (selfClosing === "/") {
    const block = [
      `${indent}<content${attributes.trimEnd()}>`,
      `${indent}  ${entry}`,
      `${indent}</content>`,
    ].join(lineEnding)
    return {changed: true, content: text.slice(0, openStart) + block + text.slice(openStart + openTag.length)}
  }

  const bodyStart = openStart + openTag.length
  const closeStart = text.indexOf("</content>", bodyStart)
  if (closeStart < 0) {
    throw new Error(`${pluginXmlPath}: <content> has no closing tag.`)
  }
  const body = text.slice(bodyStart, closeStart)
  if (!body.includes("\n")) {
    // A one-line block such as `<content></content>` becomes a block with one entry per line.
    const lines = [`${indent}<content${attributes}>`]
    if (body.trim().length > 0) {
      lines.push(`${indent}  ${body.trim()}`)
    }
    lines.push(`${indent}  ${entry}`, `${indent}</content>`)
    return {changed: true, content: text.slice(0, openStart) + lines.join(lineEnding) + text.slice(closeStart + "</content>".length)}
  }

  const entryIndent = body.match(/^([ \t]*)<module\b/m)?.[1] ?? `${indent}  `
  const closeLineStart = text.lastIndexOf("\n", closeStart) + 1
  if (text.slice(closeLineStart, closeStart).trim().length === 0) {
    return {changed: true, content: text.slice(0, closeLineStart) + entryIndent + entry + lineEnding + text.slice(closeLineStart)}
  }
  // The closing tag follows an entry on the same line, so it moves to a line of its own.
  return {changed: true, content: text.slice(0, closeStart) + lineEnding + entryIndent + entry + lineEnding + indent + text.slice(closeStart)}
}

function defaultPluginXmlPath(imlPath) {
  return resolve(dirname(imlPath), "..", "resources/META-INF/plugin.xml")
}

/**
 * The plugin descriptor that gets the new module: the `--plugin` path, else the default descriptor beside the module
 * directory. The IDE action registers only a split mode kind by default, so an `empty` module needs `--plugin`.
 */
function resolvePluginXmlPath(args, imlPath, kind, runtime) {
  if (args.pluginXmlPath !== null) {
    const pathBase = runtime.cwd ?? runtime.rootDir
    const path = isAbsolute(args.pluginXmlPath) ? resolve(args.pluginXmlPath) : resolve(pathBase, args.pluginXmlPath)
    if (!isInsidePath(path, runtime.rootDir)) {
      throw new Error(`Refusing to edit a plugin descriptor outside the repository: ${args.pluginXmlPath}`)
    }
    if (!runtime.exists(path)) {
      throw new Error(`Plugin descriptor does not exist: ${args.pluginXmlPath}`)
    }
    return path
  }
  if (kind === "empty") {
    return null
  }
  const defaultPath = defaultPluginXmlPath(imlPath)
  return runtime.exists(defaultPath) ? defaultPath : null
}

/** Reads each `modules.xml` that `register` changes, so that a broken one stops `create` before the first write. */
function checkModulesXmlFiles(imlPath, runtime) {
  for (const update of createProjectUpdates([imlPath], runtime)) {
    if (!runtime.exists(update.modulesXmlPath)) {
      throw new Error(`Project modules file does not exist: ${formatRel(update.modulesXmlPath, runtime.rootDir)}`)
    }
    parseModulesXml(runtime.readFile(update.modulesXmlPath), formatRel(update.modulesXmlPath, runtime.rootDir))
  }
}

function converterCommand(runtime) {
  // The community converter is for a community checkout. A monorepo checkout converts both halves at once.
  const isMonorepo = runtime.exists(resolve(runtime.rootDir, "community/.idea/modules.xml"))
  return isMonorepo ? "./build/jpsModelToBazel.cmd" : "./build/jpsModelToBazelCommunityOnly.cmd"
}

export function runCreate(args, runtime = createDefaultRuntime(), io = createDefaultIo()) {
  const imlPath = resolveImlPath(args.imlPaths[0], runtime, false)
  if (runtime.exists(imlPath)) {
    throw new Error(`Module file already exists: ${formatRel(imlPath, runtime.rootDir)}`)
  }
  const moduleName = basename(imlPath, ".iml")
  if (!/^[A-Za-z0-9_.-]+$/.test(moduleName)) {
    throw new Error(`Invalid module name: ${moduleName}`)
  }
  const modules = registeredModules(runtime)
  if (modules.some((module) => module.name === moduleName)) {
    throw new Error(`A module with the name ${moduleName} is already registered.`)
  }
  // Each module has `$MODULE_DIR$` as its content root, so two modules in one directory share their sources.
  const moduleDir = dirname(imlPath)
  const moduleInDir = modules.find((module) => module.dir === moduleDir)
  if (moduleInDir !== undefined) {
    throw new Error(`${formatRel(moduleDir, runtime.rootDir)} already holds the module ${moduleInDir.name}. Give the new module a directory of its own.`)
  }
  const kind = args.kind ?? inferModuleKind(moduleName)

  // Plan and check every write first, so that an error leaves no partial module behind.
  const files = planModuleFiles(imlPath, moduleName, kind, runtime)
  for (const file of files) {
    if (runtime.exists(file.path)) {
      throw new Error(`File already exists: ${formatRel(file.path, runtime.rootDir)}`)
    }
  }
  checkModulesXmlFiles(imlPath, runtime)
  const pluginXmlPath = resolvePluginXmlPath(args, imlPath, kind, runtime)
  const pluginXmlUpdate = pluginXmlPath === null
    ? null
    : addContentModuleEntry(runtime.readFile(pluginXmlPath), moduleName, kind, formatRel(pluginXmlPath, runtime.rootDir))

  for (const file of files) {
    runtime.writeFile(file.path, file.content)
    io.stdout(`Created ${formatRel(file.path, runtime.rootDir)}`)
  }

  runOperation({command: "register", imlPaths: [imlPath], fixImlEof: false}, runtime, io)

  if (pluginXmlUpdate === null) {
    const reason = kind === "empty"
      ? "An empty module goes into a plugin only with --plugin."
      : `No plugin descriptor at ${formatRel(defaultPluginXmlPath(imlPath), runtime.rootDir)}.`
    io.stdout(`${reason} For a content module, add <module name="${moduleName}"${contentModuleAttributes[kind]}/> to the <content> of the plugin.`)
  }
  else if (pluginXmlUpdate.changed) {
    runtime.writeFile(pluginXmlPath, pluginXmlUpdate.content)
    io.stdout(`Updated ${formatRel(pluginXmlPath, runtime.rootDir)}: added content module ${moduleName}`)
  }
  else {
    io.stdout(`${formatRel(pluginXmlPath, runtime.rootDir)} already has content module ${moduleName}`)
  }

  io.stdout(`Created ${kind} module ${moduleName}. Next, run ${converterCommand(runtime)}.`)
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
    return args.command === "create" ? runCreate(args, runtime, io) : runOperation(args, runtime, io)
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
