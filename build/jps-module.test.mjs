// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, match, ok} from "node:assert/strict"
import {existsSync, mkdirSync, readFileSync, writeFileSync} from "node:fs"
import {mkdir, mkdtemp, readFile, rm, writeFile} from "node:fs/promises"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import {describe, it} from "node:test"
import {
  addContentModuleEntry,
  checkFailedExitCode,
  commandExitCode,
  detectDefaultRoot,
  inferModuleKind,
  listFilesRecursively,
  parseArguments,
  renderEmptyModuleIml,
  runCli,
  updateModulesXmlContent,
  usageExitCode,
} from "./jps-module.mjs"

async function createFixtureRoot() {
  return mkdtemp(join(tmpdir(), "community-jps-module-test-"))
}

async function writeTextFile(path, content) {
  await mkdir(dirname(path), {recursive: true})
  await writeFile(path, content, "utf8")
}

function createModulesXml(moduleFilePaths) {
  return [
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
    "<project version=\"4\">",
    "  <component name=\"ProjectModuleManager\">",
    "    <modules>",
    ...moduleFilePaths.map((path) => `      <module fileurl=\"file://${path}\" filepath=\"${path}\" />`),
    "    </modules>",
    "  </component>",
    "</project>",
  ].join("\n")
}

function createRuntime(rootDir) {
  const writes = []
  return {
    runtime: {
      rootDir,
      exists: existsSync,
      readFile: (path) => readFileSync(path, "utf8"),
      writeFile: (path, content) => {
        writes.push(path)
        mkdirSync(dirname(path), {recursive: true})
        writeFileSync(path, content)
      },
      listFiles: listFilesRecursively,
    },
    writes,
  }
}

function createRecordingIo() {
  const stdout = []
  const stderr = []
  return {
    io: {
      stdout: (message) => stdout.push(message),
      stderr: (message) => stderr.push(message),
    },
    stdout,
    stderr,
  }
}

function moduleLines(content) {
  return content.split("\n").filter((line) => line.includes("<module ")).map((line) => line.trim())
}

describe("jps-module argument parsing", () => {
  it("parses register arguments", () => {
    deepEqual(parseArguments(["register", "community/foo/intellij.foo.iml", "--fix-iml-eof"]), {
      help: false,
      command: "register",
      imlPaths: ["community/foo/intellij.foo.iml"],
      fixImlEof: true,
      kind: null,
      pluginXmlPath: null,
    })
  })

  it("parses create arguments", () => {
    deepEqual(parseArguments(["create", "plugins/foo/backend/intellij.foo.backend.iml", "--kind", "backend", "--plugin", "plugins/foo/resources/META-INF/plugin.xml"]), {
      help: false,
      command: "create",
      imlPaths: ["plugins/foo/backend/intellij.foo.backend.iml"],
      fixImlEof: false,
      kind: "backend",
      pluginXmlPath: "plugins/foo/resources/META-INF/plugin.xml",
    })
  })

  it("rejects an unknown module kind", () => {
    try {
      parseArguments(["create", "foo/intellij.foo.iml", "--kind", "library"])
    }
    catch (error) {
      match(error.message, /Unknown module kind 'library'/)
      return
    }
    throw new Error("Expected parseArguments to throw")
  })

  it("rejects --kind for register", () => {
    try {
      parseArguments(["register", "foo/intellij.foo.iml", "--kind", "empty"])
    }
    catch (error) {
      match(error.message, /apply only to 'create'/)
      return
    }
    throw new Error("Expected parseArguments to throw")
  })

  it("rejects missing iml paths", () => {
    try {
      parseArguments(["register"])
    }
    catch (error) {
      match(error.message, /At least one \.iml path is required/)
      return
    }
    throw new Error("Expected parseArguments to throw")
  })
})

describe("jps-module root detection", () => {
  it("uses the ultimate root when cwd is inside its community project", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))

      equal(detectDefaultRoot(join(rootDir, "community/plugins/demo")), rootDir)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("uses the community root when no enclosing ultimate project exists", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))

      equal(detectDefaultRoot(join(rootDir, "plugins/demo")), rootDir)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })
})

describe("jps-module modules.xml updates", () => {
  it("adds entries ordered by iml basename, not by path", () => {
    const content = createModulesXml([
      "$PROJECT_DIR$/community/plugins/z/intellij.z.iml",
      "$PROJECT_DIR$/community/plugins/zz/intellij.zz.iml",
    ])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/community/plugins/a/intellij.aa.iml"])

    ok(result.changed)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/a/intellij.aa.iml\" filepath=\"$PROJECT_DIR$/community/plugins/a/intellij.aa.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" filepath=\"$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/zz/intellij.zz.iml\" filepath=\"$PROJECT_DIR$/community/plugins/zz/intellij.zz.iml\" />",
    ])
  })

  it("moves misplaced existing entries to canonical order", () => {
    const content = createModulesXml([
      "$PROJECT_DIR$/b/intellij.b.iml",
      "$PROJECT_DIR$/a/intellij.a.iml",
    ])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/a/intellij.a.iml"])

    ok(result.changed)
    match(result.diagnostics.join("\n"), /reordered module entries/)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/a/intellij.a.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/b/intellij.b.iml\" filepath=\"$PROJECT_DIR$/b/intellij.b.iml\" />",
    ])
  })

  it("orders entries like Kotlin String.compareTo", () => {
    const content = createModulesXml([
      "$PROJECT_DIR$/lower/intellij.aa.iml",
      "$PROJECT_DIR$/underscore/intellij.a_b.iml",
      "$PROJECT_DIR$/upper/intellij.aA.iml",
    ])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/lower/intellij.aa.iml"])

    ok(result.changed)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/upper/intellij.aA.iml\" filepath=\"$PROJECT_DIR$/upper/intellij.aA.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/underscore/intellij.a_b.iml\" filepath=\"$PROJECT_DIR$/underscore/intellij.a_b.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/lower/intellij.aa.iml\" filepath=\"$PROJECT_DIR$/lower/intellij.aa.iml\" />",
    ])
  })

  it("preserves relative order for equal module names", () => {
    const content = createModulesXml([
      "$PROJECT_DIR$/first/duplicate.iml",
      "$PROJECT_DIR$/second/duplicate.iml",
    ])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/third/duplicate.iml"])

    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/first/duplicate.iml\" filepath=\"$PROJECT_DIR$/first/duplicate.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/second/duplicate.iml\" filepath=\"$PROJECT_DIR$/second/duplicate.iml\" />",
      "<module fileurl=\"file://$PROJECT_DIR$/third/duplicate.iml\" filepath=\"$PROJECT_DIR$/third/duplicate.iml\" />",
    ])
  })

  it("removes duplicate filepath entries and repairs fileurl", () => {
    const content = [
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<project version=\"4\">",
      "  <component name=\"ProjectModuleManager\">",
      "    <modules>",
      "      <module fileurl=\"file://BROKEN\" filepath=\"$PROJECT_DIR$/a/intellij.a.iml\" />",
      "      <module fileurl=\"file://$PROJECT_DIR$/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/a/intellij.a.iml\" />",
      "    </modules>",
      "  </component>",
      "</project>",
    ].join("\n")

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/a/intellij.a.iml"])

    ok(result.changed)
    match(result.diagnostics.join("\n"), /fixed fileurl/)
    match(result.diagnostics.join("\n"), /removed duplicate entry/)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/a/intellij.a.iml\" />",
    ])
  })
})

describe("jps-module unregister content", () => {
  it("drops only the listed entry", () => {
    const content = createModulesXml([
      "$PROJECT_DIR$/a/intellij.a.iml",
      "$PROJECT_DIR$/b/intellij.b.iml",
    ])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/a/intellij.a.iml"], {remove: true})

    ok(result.changed)
    match(result.diagnostics.join("\n"), /removed entry for \$PROJECT_DIR\$\/a\/intellij\.a\.iml/)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/b/intellij.b.iml\" filepath=\"$PROJECT_DIR$/b/intellij.b.iml\" />",
    ])
  })

  it("never adds the listed entry back", () => {
    const content = createModulesXml(["$PROJECT_DIR$/b/intellij.b.iml"])

    const result = updateModulesXmlContent(content, "/repo", ".idea/modules.xml", ["/repo/a/intellij.a.iml"], {remove: true})

    equal(result.changed, false)
    deepEqual(moduleLines(result.content), [
      "<module fileurl=\"file://$PROJECT_DIR$/b/intellij.b.iml\" filepath=\"$PROJECT_DIR$/b/intellij.b.iml\" />",
    ])
  })
})

describe("jps-module CLI", () => {
  it("registers community modules in both project structures", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/community/plugins/z/intellij.z.iml",
      ]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/plugins/z/intellij.z.iml",
      ]))
      await writeTextFile(join(rootDir, "community/plugins/a/intellij.a.iml"), "<module></module>\n")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stdout, stderr} = createRecordingIo()
      const exitCode = await runCli(["register", "community/plugins/a/intellij.a.iml", "--fix-iml-eof"], {runtime, io})

      equal(exitCode, 0)
      equal(stderr.length, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), [
        "<root>/.idea/modules.xml",
        "<root>/community/.idea/modules.xml",
        "<root>/community/plugins/a/intellij.a.iml",
      ])
      match(stdout.join("\n"), /Updated \.idea\/modules\.xml/)
      match(stdout.join("\n"), /Updated community\/\.idea\/modules\.xml/)
      equal(await readFile(join(rootDir, "community/plugins/a/intellij.a.iml"), "utf8"), "<module></module>")
      deepEqual(moduleLines(await readFile(join(rootDir, ".idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/community/plugins/a/intellij.a.iml\" />",
        "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" filepath=\"$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" />",
      ])
      deepEqual(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/plugins/a/intellij.a.iml\" />",
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/z/intellij.z.iml\" filepath=\"$PROJECT_DIR$/plugins/z/intellij.z.iml\" />",
      ])
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("removes non-community module entries from the community project", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/../plugins/ultimate/intellij.ultimate.iml",
      ]))
      await writeTextFile(join(rootDir, "community/plugins/a/intellij.a.iml"), "<module></module>")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stdout} = createRecordingIo()
      const exitCode = await runCli(["register", "community/plugins/a/intellij.a.iml"], {runtime, io})

      equal(exitCode, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), [
        "<root>/.idea/modules.xml",
        "<root>/community/.idea/modules.xml",
      ])
      match(stdout.join("\n"), /removed entry outside project/)
      deepEqual(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/a/intellij.a.iml\" filepath=\"$PROJECT_DIR$/plugins/a/intellij.a.iml\" />",
      ])
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("resolves CLI iml paths relative to cwd while using the detected root project", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/plugins/demo/intellij.demo.iml"), "<module></module>")

      const {runtime, writes} = createRuntime(rootDir)
      runtime.cwd = join(rootDir, "community")
      const {io} = createRecordingIo()
      const exitCode = await runCli(["register", "plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), [
        "<root>/.idea/modules.xml",
        "<root>/community/.idea/modules.xml",
      ])
      deepEqual(moduleLines(await readFile(join(rootDir, ".idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/demo/intellij.demo.iml\" filepath=\"$PROJECT_DIR$/community/plugins/demo/intellij.demo.iml\" />",
      ])
      deepEqual(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/demo/intellij.demo.iml\" filepath=\"$PROJECT_DIR$/plugins/demo/intellij.demo.iml\" />",
      ])
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("does not register ultimate modules in the community project", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "plugins/demo/intellij.demo.iml"), "<module></module>")

      const {runtime, writes} = createRuntime(rootDir)
      const {io} = createRecordingIo()
      const exitCode = await runCli(["register", "plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), ["<root>/.idea/modules.xml"])
      equal(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")).length, 0)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("check reports changes without writing", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "demo/intellij.demo.iml"), "<module></module>\n")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["check", "demo/intellij.demo.iml", "--fix-iml-eof"], {runtime, io})

      equal(exitCode, checkFailedExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /JPS module registration is not canonical/)
      equal(await readFile(join(rootDir, "demo/intellij.demo.iml"), "utf8"), "<module></module>\n")
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("prints usage errors", async () => {
    const {io, stderr} = createRecordingIo()
    const exitCode = await runCli(["register"], {io})

    equal(exitCode, usageExitCode)
    match(stderr.join("\n"), /At least one \.iml path is required/)
  })

  it("unregisters a module whose iml is already gone, and keeps the other entries", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/community/plugins/a/intellij.a.iml",
        "$PROJECT_DIR$/community/plugins/z/intellij.z.iml",
      ]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/plugins/a/intellij.a.iml",
        "$PROJECT_DIR$/plugins/z/intellij.z.iml",
      ]))

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stdout, stderr} = createRecordingIo()
      // The .iml is deliberately absent: this is the old path of a renamed module.
      const exitCode = await runCli(["unregister", "community/plugins/a/intellij.a.iml"], {runtime, io})

      equal(exitCode, 0)
      equal(stderr.length, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), [
        "<root>/.idea/modules.xml",
        "<root>/community/.idea/modules.xml",
      ])
      match(stdout.join("\n"), /removed entry for \$PROJECT_DIR\$\/community\/plugins\/a\/intellij\.a\.iml/)
      deepEqual(moduleLines(await readFile(join(rootDir, ".idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" filepath=\"$PROJECT_DIR$/community/plugins/z/intellij.z.iml\" />",
      ])
      deepEqual(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/z/intellij.z.iml\" filepath=\"$PROJECT_DIR$/plugins/z/intellij.z.iml\" />",
      ])
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("unregisters an ultimate module from the root project only", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/plugins/demo/intellij.demo.iml",
      ]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))

      const {runtime, writes} = createRuntime(rootDir)
      const {io} = createRecordingIo()
      const exitCode = await runCli(["unregister", "plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, 0)
      deepEqual(writes.map((path) => path.replace(rootDir, "<root>")), ["<root>/.idea/modules.xml"])
      equal(moduleLines(await readFile(join(rootDir, ".idea/modules.xml"), "utf8")).length, 0)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("unregister writes nothing when the entry is already absent", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/plugins/z/intellij.z.iml",
      ]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stdout} = createRecordingIo()
      const exitCode = await runCli(["unregister", "plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, 0)
      equal(writes.length, 0)
      match(stdout.join("\n"), /already canonical/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("unregister rejects --fix-iml-eof", async () => {
    const {io, stderr} = createRecordingIo()
    const exitCode = await runCli(["unregister", "demo/intellij.demo.iml", "--fix-iml-eof"], {io})

    equal(exitCode, usageExitCode)
    match(stderr.join("\n"), /--fix-iml-eof does not apply to 'unregister'/)
  })
})

const templateIml = (kind) => [
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
  "<module type=\"JAVA_MODULE\" version=\"4\">",
  "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">",
  "    <content url=\"file://$MODULE_DIR$\">",
  `      <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" packagePrefix="com.intellij.remDevFeatureSample.${kind}" />`,
  "    </content>",
  ...(kind === "shared" ? [] : ["    <orderEntry type=\"module\" module-name=\"intellij.remDevFeatureSample.shared\" scope=\"RUNTIME\" />"]),
  "  </component>",
  "</module>",
].join("\n")

async function writeTemplate(rootDir, kind) {
  const templateDir = join(rootDir, `plugins/.remdev/remDevFeatureSample.${kind}`)
  await writeTextFile(join(templateDir, `intellij.remDevFeatureSample.${kind}.iml`), templateIml(kind))
  await writeTextFile(join(templateDir, `resources/intellij.remDevFeatureSample.${kind}.xml`), "<idea-plugin>\n</idea-plugin>\n")
  await writeTextFile(join(templateDir, "src/.keep"), "")
  await writeTextFile(join(templateDir, "BUILD.bazel"), "# generated\n")
}

describe("jps-module create", () => {
  it("infers the kind from the module name suffix", () => {
    equal(inferModuleKind("intellij.foo.backend"), "backend")
    equal(inferModuleKind("intellij.foo.frontend"), "frontend")
    equal(inferModuleKind("intellij.foo.shared"), "shared")
    equal(inferModuleKind("intellij.foo.backend.impl"), "empty")
  })

  it("creates an empty module with the root package as the package prefix", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "community/.idea/modules.xml"), createModulesXml([]))

      const {runtime} = createRuntime(rootDir)
      const {io, stdout} = createRecordingIo()
      const exitCode = await runCli(["create", "community/plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, 0)
      const iml = await readFile(join(rootDir, "community/plugins/demo/intellij.demo.iml"), "utf8")
      equal(iml, renderEmptyModuleIml("intellij.demo"))
      match(iml, /<sourceFolder url="file:\/\/\$MODULE_DIR\$\/src" isTestSource="false" packagePrefix="com.intellij.demo" \/>/)
      ok(!iml.endsWith("\n"))
      equal(await readFile(join(rootDir, "community/plugins/demo/resources/intellij.demo.xml"), "utf8"), "<idea-plugin>\n</idea-plugin>\n")
      ok(existsSync(join(rootDir, "community/plugins/demo/src/.keep")))
      deepEqual(moduleLines(await readFile(join(rootDir, ".idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/community/plugins/demo/intellij.demo.iml\" filepath=\"$PROJECT_DIR$/community/plugins/demo/intellij.demo.iml\" />",
      ])
      deepEqual(moduleLines(await readFile(join(rootDir, "community/.idea/modules.xml"), "utf8")), [
        "<module fileurl=\"file://$PROJECT_DIR$/plugins/demo/intellij.demo.iml\" filepath=\"$PROJECT_DIR$/plugins/demo/intellij.demo.iml\" />",
      ])
      match(stdout.join("\n"), /An empty module goes into a plugin only with --plugin/)
      match(stdout.join("\n"), /Next, run \.\/build\/jpsModelToBazel\.cmd/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("copies the template, skips BUILD.bazel and registers the module in the plugin", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTemplate(rootDir, "shared")
      await writeTextFile(join(rootDir, "plugins/foo/resources/META-INF/plugin.xml"), [
        "<idea-plugin>",
        "  <id>com.intellij.foo</id>",
        "  <content namespace=\"jetbrains\">",
        "    <module name=\"intellij.foo.core\"/>",
        "  </content>",
        "</idea-plugin>",
        "",
      ].join("\n"))

      const {runtime} = createRuntime(rootDir)
      const {io} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/foo/shared/intellij.foo.shared.iml"], {runtime, io})

      equal(exitCode, 0)
      const iml = await readFile(join(rootDir, "plugins/foo/shared/intellij.foo.shared.iml"), "utf8")
      match(iml, /packagePrefix="com\.intellij\.foo\.shared"/)
      ok(!iml.includes("remDevFeatureSample"))
      ok(existsSync(join(rootDir, "plugins/foo/shared/resources/intellij.foo.shared.xml")))
      ok(existsSync(join(rootDir, "plugins/foo/shared/src/.keep")))
      ok(!existsSync(join(rootDir, "plugins/foo/shared/BUILD.bazel")))
      equal(await readFile(join(rootDir, "plugins/foo/resources/META-INF/plugin.xml"), "utf8"), [
        "<idea-plugin>",
        "  <id>com.intellij.foo</id>",
        "  <content namespace=\"jetbrains\">",
        "    <module name=\"intellij.foo.core\"/>",
        "    <module name=\"intellij.foo.shared\" loading=\"required\"/>",
        "  </content>",
        "</idea-plugin>",
        "",
      ].join("\n"))
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("points a backend module at the shared module of its family", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([
        "$PROJECT_DIR$/plugins/foo/shared/intellij.foo.shared.iml",
      ]))
      await writeTemplate(rootDir, "backend")

      const {runtime} = createRuntime(rootDir)
      const {io} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/foo/backend/intellij.foo.backend.iml"], {runtime, io})

      equal(exitCode, 0)
      const iml = await readFile(join(rootDir, "plugins/foo/backend/intellij.foo.backend.iml"), "utf8")
      match(iml, /packagePrefix="com\.intellij\.foo\.backend"/)
      match(iml, /module-name="intellij\.foo\.shared"/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("refuses a backend module without its shared module and writes nothing", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTemplate(rootDir, "backend")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/foo/backend/intellij.foo.backend.iml"], {runtime, io})

      equal(exitCode, commandExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /intellij\.foo\.shared, which is not registered/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("adds an empty module to a plugin only with --plugin", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      const pluginXml = ["<idea-plugin>", "  <content>", "    <module name=\"intellij.foo.core\"/>", "  </content>", "</idea-plugin>"].join("\n")
      await writeTextFile(join(rootDir, "plugins/foo/resources/META-INF/plugin.xml"), pluginXml)

      const {runtime} = createRuntime(rootDir)
      equal(await runCli(["create", "plugins/foo/tests/intellij.foo.tests.iml"], {runtime, io: createRecordingIo().io}), 0)
      equal(await readFile(join(rootDir, "plugins/foo/resources/META-INF/plugin.xml"), "utf8"), pluginXml)

      equal(await runCli(["create", "plugins/foo/optional/intellij.foo.optional.iml", "--plugin", "plugins/foo/resources/META-INF/plugin.xml"],
                         {runtime, io: createRecordingIo().io}), 0)
      match(await readFile(join(rootDir, "plugins/foo/resources/META-INF/plugin.xml"), "utf8"), /    <module name="intellij\.foo\.optional"\/>\n  <\/content>/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("refuses a directory that already holds a module", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml(["$PROJECT_DIR$/plugins/foo/core/intellij.foo.core.iml"]))
      await writeTextFile(join(rootDir, "plugins/foo/core/intellij.foo.core.iml"), "<module></module>")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/foo/core/intellij.foo.core2.iml"], {runtime, io})

      equal(exitCode, commandExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /already holds the module intellij\.foo\.core/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("refuses a plugin descriptor outside the repository", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/foo/x/intellij.foo.x.iml", "--plugin", "../outside/plugin.xml"], {runtime, io})

      equal(exitCode, commandExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /outside the repository/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("writes nothing when the community modules.xml is missing", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await mkdir(join(rootDir, "community"), {recursive: true})

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["create", "community/plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, commandExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /community\/\.idea\/modules\.xml/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("refuses an existing module file", async () => {
    const rootDir = await createFixtureRoot()
    try {
      await writeTextFile(join(rootDir, ".idea/modules.xml"), createModulesXml([]))
      await writeTextFile(join(rootDir, "plugins/demo/intellij.demo.iml"), "<module></module>")

      const {runtime, writes} = createRuntime(rootDir)
      const {io, stderr} = createRecordingIo()
      const exitCode = await runCli(["create", "plugins/demo/intellij.demo.iml"], {runtime, io})

      equal(exitCode, commandExitCode)
      equal(writes.length, 0)
      match(stderr.join("\n"), /already exists/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })
})

describe("jps-module content module entries", () => {
  it("expands a self-closing content tag", () => {
    const text = [
      "<idea-plugin>",
      "  <!--suppress PluginXmlValidity -->",
      "  <content namespace=\"jetbrains\" />",
      "</idea-plugin>",
    ].join("\n")

    const result = addContentModuleEntry(text, "intellij.foo.frontend", "frontend", "plugin.xml")

    equal(result.content, [
      "<idea-plugin>",
      "  <!--suppress PluginXmlValidity -->",
      "  <content namespace=\"jetbrains\">",
      "    <module name=\"intellij.foo.frontend\" required-if-available=\"intellij.platform.frontend\"/>",
      "  </content>",
      "</idea-plugin>",
    ].join("\n"))
  })

  it("adds a content block before the dependencies", () => {
    const text = [
      "<idea-plugin>",
      "  <id>com.intellij.foo</id>",
      "",
      "  <dependencies>",
      "    <plugin id=\"com.intellij.modules.lang\"/>",
      "  </dependencies>",
      "</idea-plugin>",
    ].join("\n")

    const result = addContentModuleEntry(text, "intellij.foo.optional", "empty", "plugin.xml")

    equal(result.content, [
      "<idea-plugin>",
      "  <id>com.intellij.foo</id>",
      "",
      "  <content namespace=\"jetbrains\">",
      "    <module name=\"intellij.foo.optional\"/>",
      "  </content>",
      "",
      "  <dependencies>",
      "    <plugin id=\"com.intellij.modules.lang\"/>",
      "  </dependencies>",
      "</idea-plugin>",
    ].join("\n"))
  })

  it("turns a one-line content block into a block", () => {
    const text = ["<idea-plugin>", "  <content namespace=\"jetbrains\"><module name=\"intellij.foo.a\"/></content>", "</idea-plugin>"].join("\n")

    const result = addContentModuleEntry(text, "intellij.foo.b", "empty", "plugin.xml")

    equal(result.content, [
      "<idea-plugin>",
      "  <content namespace=\"jetbrains\">",
      "    <module name=\"intellij.foo.a\"/>",
      "    <module name=\"intellij.foo.b\"/>",
      "  </content>",
      "</idea-plugin>",
    ].join("\n"))
  })

  it("keeps an entry of a second content block", () => {
    const text = [
      "<idea-plugin>",
      "  <content>",
      "    <module name=\"intellij.foo.a\"/>",
      "  </content>",
      "  <content namespace=\"jetbrains\">",
      "    <module name=\"intellij.foo.b\" loading=\"required\"/>",
      "  </content>",
      "</idea-plugin>",
    ].join("\n")

    equal(addContentModuleEntry(text, "intellij.foo.b", "shared", "plugin.xml").changed, false)
  })

  it("keeps an existing entry", () => {
    const text = [
      "<idea-plugin>",
      "  <content>",
      "    <module name=\"intellij.foo.backend\" required-if-available=\"intellij.platform.backend\"/>",
      "  </content>",
      "</idea-plugin>",
    ].join("\n")

    const result = addContentModuleEntry(text, "intellij.foo.backend", "backend", "plugin.xml")

    equal(result.changed, false)
    equal(result.content, text)
  })
})
