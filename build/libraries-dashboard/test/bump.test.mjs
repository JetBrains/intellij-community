// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, ok, rejects, throws} from "node:assert/strict"
import {describe, it} from "node:test"
import {mkdtempSync, mkdirSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {
  bumpFile,
  followUpCommands,
  mirrorDrift,
  mirrorVersions,
  parseArgs,
  parseCoordinate,
  pomDependencies,
  repositoryUrl,
  rewriteLibraryBlock,
  rewriteMirror,
  VERSION_MIRRORS,
} from "../libraries-dashboard.mjs"

// A multi-artifact block in the mockk layout, plus a root pinned to a different version.
const IML = `<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <orderEntry type="module-library" exported="">
      <library name="io.mockk" type="repository">
        <properties maven-id="io.mockk:mockk:1.14.5">
          <verification>
            <artifact url="file://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.5/mockk-1.14.5.jar">
              <sha256sum>0000000000000000000000000000000000000000000000000000000000000001</sha256sum>
            </artifact>
            <artifact url="file://$MAVEN_REPOSITORY$/io/mockk/mockk-dsl/1.14.5/mockk-dsl-1.14.5.jar">
              <sha256sum>0000000000000000000000000000000000000000000000000000000000000002</sha256sum>
            </artifact>
            <artifact url="file://$MAVEN_REPOSITORY$/org/objenesis/objenesis/3.4/objenesis-3.4.jar">
              <sha256sum>0000000000000000000000000000000000000000000000000000000000000003</sha256sum>
            </artifact>
          </verification>
          <exclude>
            <dependency maven-id="org.jetbrains.kotlin:kotlin-stdlib" />
          </exclude>
        </properties>
        <CLASSES>
          <root url="jar://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.5/mockk-1.14.5.jar!/" />
          <root url="jar://$MAVEN_REPOSITORY$/io/mockk/mockk-dsl/1.14.5/mockk-dsl-1.14.5.jar!/" />
          <root url="jar://$MAVEN_REPOSITORY$/org/objenesis/objenesis/3.4/objenesis-3.4.jar!/" />
        </CLASSES>
        <JAVADOC />
        <SOURCES>
          <root url="jar://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.5/mockk-1.14.5-sources.jar!/" />
          <root url="jar://$MAVEN_REPOSITORY$/io/mockk/mockk-dsl/1.14.5/mockk-dsl-1.14.5-sources.jar!/" />
        </SOURCES>
      </library>
    </orderEntry>
    <orderEntry type="module-library">
      <library name="other" type="repository">
        <properties maven-id="org.example:other:1.14.5">
          <verification>
            <artifact url="file://$MAVEN_REPOSITORY$/org/example/other/1.14.5/other-1.14.5.jar">
              <sha256sum>0000000000000000000000000000000000000000000000000000000000000004</sha256sum>
            </artifact>
          </verification>
        </properties>
        <CLASSES>
          <root url="jar://$MAVEN_REPOSITORY$/org/example/other/1.14.5/other-1.14.5.jar!/" />
        </CLASSES>
      </library>
    </orderEntry>
  </component>
</module>`

const COORDS = { groupId: "io.mockk", artifactId: "mockk", oldVersion: "1.14.5", newVersion: "1.14.11" }

describe("rewriteLibraryBlock", () => {
  const block = IML.match(/<library name="io.mockk"[\s\S]*?<\/library>/)[0]

  it("moves maven-id and every same-version URL, leaves other versions alone", () => {
    const { block: out, artifactUrls } = rewriteLibraryBlock(block, COORDS)
    ok(out.includes('maven-id="io.mockk:mockk:1.14.11"'))
    ok(out.includes("/io/mockk/mockk/1.14.11/mockk-1.14.11.jar"))
    ok(out.includes("/io/mockk/mockk-dsl/1.14.11/mockk-dsl-1.14.11-sources.jar!/"))
    ok(out.includes("/org/objenesis/objenesis/3.4/objenesis-3.4.jar"))
    equal(out.includes("1.14.5"), false)
    deepEqual(artifactUrls, [
      "file://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.11/mockk-1.14.11.jar",
      "file://$MAVEN_REPOSITORY$/io/mockk/mockk-dsl/1.14.11/mockk-dsl-1.14.11.jar",
      "file://$MAVEN_REPOSITORY$/org/objenesis/objenesis/3.4/objenesis-3.4.jar",
    ])
  })

  it("refuses a block without the expected maven-id", () => {
    throws(() => rewriteLibraryBlock(block, { ...COORDS, oldVersion: "1.0.0" }), /has no maven-id/)
  })
})

describe("bumpFile", () => {
  const sums = new Map([
    ["file://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.11/mockk-1.14.11.jar", "a".repeat(64)],
    ["file://$MAVEN_REPOSITORY$/io/mockk/mockk-dsl/1.14.11/mockk-dsl-1.14.11.jar", "b".repeat(64)],
    ["file://$MAVEN_REPOSITORY$/org/objenesis/objenesis/3.4/objenesis-3.4.jar", "c".repeat(64)],
  ])

  it("rewrites only the matching block and keeps the file tail byte-exact", async () => {
    const { content, artifacts } = await bumpFile(IML, COORDS, async url => sums.get(url))
    equal(artifacts, 3)
    ok(content.includes(`<sha256sum>${"a".repeat(64)}</sha256sum>`))
    ok(content.includes(`<sha256sum>${"b".repeat(64)}</sha256sum>`))
    ok(content.includes(`<sha256sum>${"c".repeat(64)}</sha256sum>`))
    // The unrelated library with the same version string is untouched.
    ok(content.includes('maven-id="org.example:other:1.14.5"'))
    ok(content.includes("/org/example/other/1.14.5/other-1.14.5.jar"))
    ok(content.includes("0000000000000000000000000000000000000000000000000000000000000004"))
    ok(content.endsWith("</module>"))
    equal(content.endsWith("\n"), false)
    equal(content.split("\n").length, IML.split("\n").length)
  })

  it("fails without writing when a checksum is missing", async () => {
    await rejects(bumpFile(IML, COORDS, async () => null), /no sha256 for/)
  })

  it("fails when the file does not pin the old version", async () => {
    await rejects(bumpFile(IML, { ...COORDS, oldVersion: "1.0.0" }, async () => "x"), /no repository library/)
  })
})

describe("helpers", () => {
  it("maps a $MAVEN_REPOSITORY$ URL onto the repository", () => {
    equal(
      repositoryUrl("file://$MAVEN_REPOSITORY$/io/mockk/mockk/1.14.11/mockk-1.14.11.jar", "https://repo1.maven.org/maven2"),
      "https://repo1.maven.org/maven2/io/mockk/mockk/1.14.11/mockk-1.14.11.jar"
    )
  })

  it("parses the bump command line", () => {
    const opts = parseArgs(["bun", "x.mjs", "bump", "io.mockk:mockk=1.14.11", "org.jspecify:jspecify", "--kind=wrapper", "--refresh"])
    equal(opts.command, "bump")
    deepEqual(opts.coordinates, ["io.mockk:mockk=1.14.11", "org.jspecify:jspecify"])
    equal(opts.kind, "wrapper")
    equal(opts.refresh, true)
    const report = parseArgs(["bun", "x.mjs", "--format=json"])
    equal(report.command, "report")
    equal(report.format, "json")
  })

  it("parses coordinates with and without a version", () => {
    deepEqual(parseCoordinate("io.mockk:mockk"), { groupId: "io.mockk", artifactId: "mockk", version: null })
    deepEqual(parseCoordinate("io.mockk:mockk=1.14.11"), { groupId: "io.mockk", artifactId: "mockk", version: "1.14.11" })
    throws(() => parseCoordinate("mockk"), /bad coordinate/)
  })

  it("lists compile and runtime POM dependencies only", () => {
    const pom = `<project><dependencies>
      <dependency><groupId>a</groupId><artifactId>b</artifactId><version>1</version></dependency>
      <dependency><groupId>a</groupId><artifactId>c</artifactId><version>2</version><scope>runtime</scope></dependency>
      <dependency><groupId>a</groupId><artifactId>d</artifactId><version>3</version><scope>test</scope></dependency>
      <dependency><groupId>a</groupId><artifactId>e</artifactId><version>4</version><optional>true</optional></dependency>
    </dependencies></project>`
    deepEqual(pomDependencies(pom), ["a:b:1 (compile)", "a:c:2 (runtime)"])
  })
})

describe("followUpCommands", () => {
  it("names the Fleet dump and both lockfile updates between the JPS generator and the format check when the checkout has the Fleet generator", () => {
    const root = mkdtempSync(join(tmpdir(), "libraries-dashboard-"))
    mkdirSync(join(root, "fleet/build"), { recursive: true })
    writeFileSync(join(root, "fleet/build/generateProjectModel.cmd"), "")
    deepEqual(followUpCommands(root), [
      "./build/jpsModelToBazel.cmd",
      "./fleet/build/generateProjectModel.cmd dump",
      "./bazel.cmd mod deps --lockfile_mode=update",
      "(cd community && ./bazel.cmd mod deps --lockfile_mode=update)",
      "bazel run //:format.check",
    ])
  })

  it("skips the Fleet dump and the lockfile updates in a checkout without the Fleet generator", () => {
    const root = mkdtempSync(join(tmpdir(), "libraries-dashboard-"))
    deepEqual(followUpCommands(root), ["./build/jpsModelToBazel.cmd", "bazel run //:format.check"])
  })
})

// The two version copies outside the JPS model, in the layout of the real files.
const POM = `<project>
  <properties>
    <bootstrap.intellij.version>263.3305</bootstrap.intellij.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>commons-codec</groupId>
      <artifactId>commons-codec</artifactId>
      <version>1.21.0</version>
    </dependency>
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.13.2</version>
    </dependency>
    <dependency>
      <groupId>com.jetbrains.intellij.platform</groupId>
      <artifactId>jps-build</artifactId>
      <version>\${bootstrap.intellij.version}</version>
    </dependency>
  </dependencies>
</project>`

const JAVA = `public final class JetBrainsAnnotationsExternalLibraryResolver {
  private static final String JAVA5_VERSION = "24.0.0";
  private static final String VERSION = "26.0.2";
}`

const POM_MIRROR = VERSION_MIRRORS.find(m => m.kind === "pom")
const CONSTANT_MIRROR = VERSION_MIRRORS.find(m => m.kind === "constant")

describe("version mirrors", () => {
  it("lists the literal POM versions and skips a property reference", () => {
    deepEqual(mirrorVersions(POM, POM_MIRROR), [
      { groupId: "commons-codec", artifactId: "commons-codec", version: "1.21.0" },
      { groupId: "com.google.code.gson", artifactId: "gson", version: "2.13.2" },
    ])
  })

  it("lists the annotations constant, not the java5 one", () => {
    deepEqual(mirrorVersions(JAVA, CONSTANT_MIRROR), [{ groupId: "org.jetbrains", artifactId: "annotations", version: "26.0.2" }])
  })

  it("rewrites one POM dependency and leaves the others byte-exact", () => {
    const { content, from } = rewriteMirror(POM, POM_MIRROR, { groupId: "com.google.code.gson", artifactId: "gson", newVersion: "2.14.0" })
    equal(from, "2.13.2")
    ok(content.includes("<artifactId>gson</artifactId>\n      <version>2.14.0</version>"))
    ok(content.includes("<version>1.21.0</version>"))
    ok(content.includes("<version>\${bootstrap.intellij.version}</version>"))
    equal(content.split("\n").length, POM.split("\n").length)
  })

  it("rewrites the annotations constant only for org.jetbrains:annotations", () => {
    const { content, from } = rewriteMirror(JAVA, CONSTANT_MIRROR, { groupId: "org.jetbrains", artifactId: "annotations", newVersion: "26.1.0" })
    equal(from, "26.0.2")
    ok(content.includes('VERSION = "26.1.0";'))
    ok(content.includes('JAVA5_VERSION = "24.0.0";'))
    equal(rewriteMirror(JAVA, CONSTANT_MIRROR, { groupId: "org.jetbrains", artifactId: "annotations-java5", newVersion: "26.1.0" }), null)
  })

  it("returns null when the POM has no copy of the library", () => {
    equal(rewriteMirror(POM, POM_MIRROR, { groupId: "io.mockk", artifactId: "mockk", newVersion: "1.14.11" }), null)
  })

  it("reports a copy that differs from the pinned version", () => {
    const pinned = new Map([["commons-codec:commons-codec", "1.22.1"], ["com.google.code.gson:gson", "2.13.2"]])
    deepEqual(mirrorDrift(POM, POM_MIRROR, pinned), [
      { path: POM_MIRROR.path, groupId: "commons-codec", artifactId: "commons-codec", version: "1.21.0", pinned: "1.22.1" },
    ])
  })

  it("matches the real mirror files", async () => {
    const { readFile } = await import("node:fs/promises")
    const { REPO_ROOT } = await import("../libraries-dashboard.mjs")
    for (const mirror of VERSION_MIRRORS) {
      const copies = mirrorVersions(await readFile(join(REPO_ROOT, mirror.path), "utf8"), mirror)
      ok(copies.length > 0, `${mirror.path} has no version copy`)
    }
  })
})
