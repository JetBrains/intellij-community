// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal} from "node:assert/strict"
import {describe, it} from "node:test"
import {
  blockArtifacts,
  checkArtifactSnapshot,
  exactVersion,
  findLibraryBlock,
  formatSnapshotProblem,
  parseArgs,
  pomDirectDependencies,
  pomProjectCoordinates,
  resolvePomValue,
} from "../libraries-dashboard.mjs"

// The mockito wrapper after the 5.23.0 bump: the agent pinned at 1.18.13 while the POM declares 1.17.7.
const MOCKITO_BLOCK = `<library name="mockito" type="repository">
        <properties maven-id="org.mockito:mockito-core:5.23.0">
          <verification>
            <artifact url="file://$MAVEN_REPOSITORY$/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar">
              <sha256sum>ae295bebd5d11fab97ab297815dc7617188b86003cbce3dfd5c0d5c3a6cc4a0c</sha256sum>
            </artifact>
            <artifact url="file://$MAVEN_REPOSITORY$/net/bytebuddy/byte-buddy-agent/1.18.13/byte-buddy-agent-1.18.13.jar">
              <sha256sum>990c630297fb8c84fea5b16085a4b3035b81748084a94a98f0b9dc0c5d63d499</sha256sum>
            </artifact>
            <artifact url="file://$MAVEN_REPOSITORY$/org/objenesis/objenesis/3.3/objenesis-3.3.jar">
              <sha256sum>02dfd0b0439a5591e35b708ed2f5474eb0948f53abf74637e959b8e4ef69bfeb</sha256sum>
            </artifact>
          </verification>
          <exclude>
            <dependency maven-id="net.bytebuddy:byte-buddy" />
          </exclude>
        </properties>
        <CLASSES>
          <root url="jar://$MAVEN_REPOSITORY$/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar!/" />
        </CLASSES>
      </library>`

const MOCKITO_POM = `<project>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <version>5.23.0</version>
  <dependencies>
    <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy</artifactId><version>1.17.7</version><scope>compile</scope></dependency>
    <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy-agent</artifactId><version>1.17.7</version><scope>compile</scope></dependency>
    <dependency><groupId>org.objenesis</groupId><artifactId>objenesis</artifactId><version>3.3</version><scope>runtime</scope></dependency>
  </dependencies>
</project>`

const MOCKITO_DEPS = pomDirectDependencies(MOCKITO_POM)

describe("pomDirectDependencies", () => {
  it("lists compile and runtime jar dependencies with their resolved versions", () => {
    deepEqual(MOCKITO_DEPS, [
      { groupId: "net.bytebuddy", artifactId: "byte-buddy", version: "1.17.7", scope: "compile", classifier: null },
      { groupId: "net.bytebuddy", artifactId: "byte-buddy-agent", version: "1.17.7", scope: "compile", classifier: null },
      { groupId: "org.objenesis", artifactId: "objenesis", version: "3.3", scope: "runtime", classifier: null },
    ])
  })

  it("skips test, provided and optional dependencies, POM-typed dependencies and dependencyManagement", () => {
    const pom = `<project><version>1</version>
      <dependencyManagement><dependencies>
        <dependency><groupId>m</groupId><artifactId>managed</artifactId><version>9</version></dependency>
      </dependencies></dependencyManagement>
      <dependencies>
        <dependency><groupId>a</groupId><artifactId>b</artifactId><version>1</version></dependency>
        <dependency><groupId>a</groupId><artifactId>t</artifactId><version>1</version><scope>test</scope></dependency>
        <dependency><groupId>a</groupId><artifactId>p</artifactId><version>1</version><scope>provided</scope></dependency>
        <dependency><groupId>a</groupId><artifactId>o</artifactId><version>1</version><optional>true</optional></dependency>
        <dependency><groupId>a</groupId><artifactId>bom</artifactId><version>1</version><type>pom</type></dependency>
      </dependencies></project>`
    deepEqual(pomDirectDependencies(pom).map(d => d.artifactId), ["b"])
  })

  it("ignores the dependencies of plugins and profiles and a commented-out dependency", () => {
    // opencsv 5.12.0 lists JUnit engines for surefire; batik comments a dependency out.
    const pom = `<project><version>1</version>
      <build><plugins><plugin><dependencies>
        <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId><version>5</version><scope>runtime</scope></dependency>
      </dependencies></plugin></plugins></build>
      <profiles><profile><dependencies>
        <dependency><groupId>p</groupId><artifactId>profile-only</artifactId><version>1</version></dependency>
      </dependencies></profile></profiles>
      <dependencies>
        <!--<dependency><groupId>a</groupId><artifactId>commented</artifactId><version>1</version></dependency>-->
        <dependency><groupId>a</groupId><artifactId>real</artifactId><version>1</version></dependency>
      </dependencies></project>`
    deepEqual(pomDirectDependencies(pom).map(d => d.artifactId), ["real"])
  })

  it("resolves project.version, project.groupId and a property of the same POM", () => {
    const pom = `<project>
      <parent><groupId>org.parent</groupId><artifactId>parent</artifactId><version>7</version></parent>
      <artifactId>child</artifactId>
      <properties><dep.version>2.5</dep.version></properties>
      <dependencies>
        <dependency><groupId>\${project.groupId}</groupId><artifactId>sibling</artifactId><version>\${project.version}</version></dependency>
        <dependency><groupId>x</groupId><artifactId>y</artifactId><version>\${dep.version}</version></dependency>
        <dependency><groupId>x</groupId><artifactId>z</artifactId><version>\${parent.only}</version></dependency>
        <dependency><groupId>x</groupId><artifactId>managed</artifactId></dependency>
      </dependencies></project>`
    deepEqual(pomProjectCoordinates(pom), { groupId: "org.parent", version: "7" })
    equal(resolvePomValue("\${project.groupId}:\${dep.version}", pom), "org.parent:2.5")
    equal(resolvePomValue("\${parent.only}", pom), null)
    deepEqual(
      pomDirectDependencies(pom).map(d => [d.groupId, d.artifactId, d.version]),
      [
        ["org.parent", "sibling", "7"],
        ["x", "y", "2.5"],
        ["x", "z", null],
        ["x", "managed", null],
      ]
    )
  })

  it("reads a hard version requirement as one version and an open range as none", () => {
    // androidx.compose.runtime:runtime-desktop:1.12.0 declares runtime-annotation-jvm as [1.12.0].
    equal(exactVersion("[1.12.0]"), "1.12.0")
    equal(exactVersion("[ 1.12.0 ]"), "1.12.0")
    equal(exactVersion("[1.0,2.0)"), null)
    equal(exactVersion("(,1.0]"), null)
    equal(exactVersion("1.12.0"), "1.12.0")
    equal(exactVersion(null), null)
  })
})

describe("blockArtifacts and findLibraryBlock", () => {
  it("reads the coordinates of every <artifact>, with a nested group path", () => {
    deepEqual(blockArtifacts(MOCKITO_BLOCK), [
      { groupId: "org.mockito", artifactId: "mockito-core", version: "5.23.0" },
      { groupId: "net.bytebuddy", artifactId: "byte-buddy-agent", version: "1.18.13" },
      { groupId: "org.objenesis", artifactId: "objenesis", version: "3.3" },
    ])
  })

  it("finds the block by its maven-id and returns null for another version", () => {
    const content = `<module>\n    <orderEntry type="module-library">\n      ${MOCKITO_BLOCK}\n    </orderEntry>\n</module>`
    equal(findLibraryBlock(content, "org.mockito:mockito-core:5.23.0"), MOCKITO_BLOCK)
    equal(findLibraryBlock(content, "org.mockito:mockito-core:5.19.0"), null)
  })
})

describe("checkArtifactSnapshot", () => {
  it("reports an artifact pinned at another version than the POM declares", () => {
    const { problems, notes } = checkArtifactSnapshot(MOCKITO_BLOCK, MOCKITO_DEPS)
    deepEqual(problems, [
      { kind: "version", groupId: "net.bytebuddy", artifactId: "byte-buddy-agent", pinned: "1.18.13", declared: "1.17.7", scope: "compile" },
    ])
    deepEqual(notes, [])
    equal(formatSnapshotProblem(problems[0]), "net.bytebuddy:byte-buddy-agent is pinned at 1.18.13, the POM declares 1.17.7 (compile)")
  })

  it("accepts the block once the dependency is excluded", () => {
    const fixed = MOCKITO_BLOCK.replace(
      `<dependency maven-id="net.bytebuddy:byte-buddy" />`,
      `<dependency maven-id="net.bytebuddy:byte-buddy" />\n            <dependency maven-id="net.bytebuddy:byte-buddy-agent" />`
    ).replace(/\s*<artifact url="[^"]*byte-buddy-agent[^"]*">\s*<sha256sum>[0-9a-f]+<\/sha256sum>\s*<\/artifact>/, "")
    deepEqual(checkArtifactSnapshot(fixed, MOCKITO_DEPS), { problems: [], notes: [] })
  })

  it("accepts the block once the artifact is pinned at the declared version", () => {
    const fixed = MOCKITO_BLOCK.replaceAll("1.18.13", "1.17.7")
    deepEqual(checkArtifactSnapshot(fixed, MOCKITO_DEPS), { problems: [], notes: [] })
  })

  it("reports a direct dependency without an artifact and without an exclude", () => {
    const block = MOCKITO_BLOCK.replace(/\s*<artifact url="[^"]*objenesis[^"]*">\s*<sha256sum>[0-9a-f]+<\/sha256sum>\s*<\/artifact>/, "")
    const { problems } = checkArtifactSnapshot(block, MOCKITO_DEPS.filter(d => d.artifactId !== "byte-buddy-agent"))
    deepEqual(problems, [{ kind: "missing", groupId: "org.objenesis", artifactId: "objenesis", declared: "3.3", scope: "runtime" }])
    equal(
      formatSnapshotProblem(problems[0]),
      "org.objenesis:objenesis:3.3 (runtime) is a direct dependency without an <artifact> and without an <exclude>"
    )
  })

  it("has nothing to compare when the block excludes transitive dependencies", () => {
    const block = MOCKITO_BLOCK.replace(`<properties maven-id=`, `<properties include-transitive-deps="false" maven-id=`)
    deepEqual(checkArtifactSnapshot(block, MOCKITO_DEPS), { problems: [], notes: [] })
  })

  it("notes a dependency without one exact version instead of failing on it", () => {
    const deps = [{ groupId: "org.objenesis", artifactId: "objenesis", version: null, scope: "runtime", classifier: null }]
    const { problems, notes } = checkArtifactSnapshot(MOCKITO_BLOCK, deps)
    deepEqual(problems, [])
    deepEqual(notes, ["org.objenesis:objenesis: the POM does not name one exact version (a parent property or a range); compare it by hand"])
  })

  it("skips a dependency with a classifier", () => {
    const deps = [{ groupId: "org.objenesis", artifactId: "objenesis", version: "9.9", scope: "runtime", classifier: "natives" }]
    deepEqual(checkArtifactSnapshot(MOCKITO_BLOCK, deps), { problems: [], notes: [] })
  })
})

describe("check command arguments", () => {
  it("parses check with optional coordinates and --kind", () => {
    const all = parseArgs(["bun", "x.mjs", "check"])
    equal(all.command, "check")
    deepEqual(all.coordinates, [])
    const named = parseArgs(["bun", "x.mjs", "check", "org.mockito:mockito-core", "--kind=wrapper"])
    equal(named.command, "check")
    deepEqual(named.coordinates, ["org.mockito:mockito-core"])
    equal(named.kind, "wrapper")
  })
})
