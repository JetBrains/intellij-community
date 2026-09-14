// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, ok, rejects, throws} from "node:assert/strict"
import {describe, it} from "node:test"
import {bumpFile, parseArgs, parseCoordinate, pomDependencies, repositoryUrl, rewriteLibraryBlock} from "../libraries-dashboard.mjs"

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
