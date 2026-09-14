// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal} from "node:assert/strict"
import {describe, it} from "node:test"
import {githubUrlFromPom, groupByGA, normalizeGithubUrl, parseIml, repoLabel} from "../libraries-dashboard.mjs"

const WRAPPER = `<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <orderEntry type="module-library" exported="">
      <library type="repository" name="asm">
        <properties include-transitive-deps="false" maven-id="org.jetbrains.intellij.deps:asm-all:9.6.1">
          <verification>
            <artifact url="file://$MAVEN_REPOSITORY$/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.jar">
              <sha256sum>a72e84efb1406a7ab326e0b28c4376e9e1ebfc08c09f23edff5e6e7249588df7</sha256sum>
            </artifact>
          </verification>
        </properties>
        <CLASSES>
          <root url="jar://$MAVEN_REPOSITORY$/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.jar!/" />
        </CLASSES>
      </library>
    </orderEntry>
    <orderEntry type="module-library">
      <library name="plain">
        <CLASSES><root url="jar://$PROJECT_DIR$/lib/plain.jar!/" /></CLASSES>
      </library>
    </orderEntry>
  </component>
</module>`

const PROJECT = `<component name="libraryTable">
  <library name="kotlin-script-runtime" type="repository">
    <properties maven-id="org.jetbrains.kotlin:kotlin-script-runtime:2.4.20-RC3">
      <verification>
        <artifact url="file://$MAVEN_REPOSITORY$/org/jetbrains/kotlin/kotlin-script-runtime/2.4.20-RC3/kotlin-script-runtime-2.4.20-RC3.jar">
          <sha256sum>453eb304a477b6ccd50ae70959e2d67b3bc37d2112146003bed97c25a7cc691b</sha256sum>
        </artifact>
      </verification>
    </properties>
  </library>
</component>`

describe("parseIml", () => {
  it("reads the maven-id of every repository library and tolerates attribute order", () => {
    const libs = parseIml(WRAPPER, "/repo/community/libraries/asm/intellij.libraries.asm.iml")
    equal(libs.length, 1)
    deepEqual(
      { ...libs[0] },
      {
        groupId: "org.jetbrains.intellij.deps",
        artifactId: "asm-all",
        version: "9.6.1",
        libraryName: "asm",
        filePath: "/repo/community/libraries/asm/intellij.libraries.asm.iml",
        kind: "wrapper",
      }
    )
  })
  it("reads a project library xml with the project kind", () => {
    const libs = parseIml(PROJECT, "/repo/community/.idea/libraries/kotlin_script_runtime.xml", "project")
    equal(libs.length, 1)
    equal(libs[0].version, "2.4.20-RC3")
    equal(libs[0].libraryName, "kotlin-script-runtime")
    equal(libs[0].kind, "project")
  })
})

describe("groupByGA", () => {
  it("merges wrapper and project pins and names each source", () => {
    const entries = [
      ...parseIml(WRAPPER, "/repo/community/libraries/asm/intellij.libraries.asm.iml"),
      ...parseIml(PROJECT, "/repo/community/.idea/libraries/kotlin_script_runtime.xml", "project"),
      {
        groupId: "org.jetbrains.kotlin",
        artifactId: "kotlin-script-runtime",
        version: "2.5.0-dev-6810",
        libraryName: "kotlin-script-runtime",
        filePath: "/repo/community/libraries/kotlinc/kotlin-script-runtime/intellij.libraries.kotlinc.kotlin.script.runtime.iml",
        kind: "wrapper",
      },
    ]
    const groups = groupByGA(entries)
    equal(groups.length, 2)
    const kotlin = groups.find(g => g.artifactId === "kotlin-script-runtime")
    deepEqual(kotlin.versions, ["2.4.20-RC3", "2.5.0-dev-6810"])
    deepEqual(
      kotlin.modules.map(m => [m.module, m.kind]),
      [
        ["kotlin-script-runtime", "project"],
        ["kotlinc.kotlin.script.runtime", "wrapper"],
      ]
    )
  })
})

describe("GitHub link derivation", () => {
  it("normalizes scm and plain URLs", () => {
    equal(normalizeGithubUrl("scm:git:git@github.com:jhy/jsoup.git"), "https://github.com/jhy/jsoup")
    equal(normalizeGithubUrl("git://github.com/square/okio.git/"), "https://github.com/square/okio")
    equal(normalizeGithubUrl("https://github.com/google/guava/tree/master/guava"), "https://github.com/google/guava")
    equal(normalizeGithubUrl("https://example.org/x"), null)
  })
  it("prefers scm over the project url", () => {
    const pom = `<project><url>https://github.com/other/site</url><scm><url>https://github.com/jhy/jsoup</url></scm></project>`
    equal(githubUrlFromPom(pom), "https://github.com/jhy/jsoup")
    equal(githubUrlFromPom(`<project><url>https://github.com/a/b</url></project>`), "https://github.com/a/b")
    equal(githubUrlFromPom(`<project><url>https://example.org</url></project>`), null)
  })
})

describe("repoLabel", () => {
  it("shortens the repository URLs", () => {
    equal(repoLabel("https://repo1.maven.org/maven2"), "central")
    equal(repoLabel("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"), "ij/intellij-dependencies")
    equal(repoLabel("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev"), "compose/dev")
    equal(repoLabel("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2"), "google")
    equal(repoLabel(null), "—")
  })
})
