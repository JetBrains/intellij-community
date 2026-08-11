// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, match, ok} from "node:assert/strict"
import {existsSync, readFileSync, writeFileSync} from "node:fs"
import {mkdir, mkdtemp, readFile, rm, writeFile} from "node:fs/promises"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import {describe, it} from "node:test"
import {
  checkFailedExitCode,
  detectDefaultRoot,
  parseArguments,
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
        writeFileSync(path, content)
      },
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
    })
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
})
