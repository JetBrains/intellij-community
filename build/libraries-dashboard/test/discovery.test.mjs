// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual} from "node:assert/strict"
import {describe, it} from "node:test"
import {parseModulesXml, parseRepositories} from "../libraries-dashboard.mjs"

const MODULES = `<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="file://$PROJECT_DIR$/fleet/andel/fleet.andel.iml" filepath="$PROJECT_DIR$/fleet/andel/fleet.andel.iml" />
      <module fileurl="file://$PROJECT_DIR$/libraries/jansi/intellij.libraries.jansi.iml" filepath="$PROJECT_DIR$/libraries/jansi/intellij.libraries.jansi.iml" />
      <module fileurl="file://$PROJECT_DIR$/plugins/bazel/aspect-sdk/intellij.libraries.bazel.aspect.sdk.iml" filepath="$PROJECT_DIR$/plugins/bazel/aspect-sdk/intellij.libraries.bazel.aspect.sdk.iml" />
      <module fileurl="file://$PROJECT_DIR$/platform/x/intellij.platform.libraries.helper.iml" filepath="$PROJECT_DIR$/platform/x/intellij.platform.libraries.helper.iml" />
    </modules>
  </component>
</project>`

const REPOS = `<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="RemoteRepositoriesConfiguration">
    <remote-repository>
      <option name="id" value="central-proxy" />
      <option name="name" value="Maven Central Proxy" />
      <option name="url" value="https://cache-redirector.jetbrains.com/repo1.maven.org/maven2" />
    </remote-repository>
    <remote-repository>
      <option name="id" value="intellij-dependencies" />
      <option name="name" value="IntelliJ Dependencies" />
      <option name="url" value="https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/" />
    </remote-repository>
  </component>
</project>`

describe("parseModulesXml", () => {
  it("returns only wrapper module paths, wherever they live", () => {
    deepEqual(parseModulesXml(MODULES), [
      "libraries/jansi/intellij.libraries.jansi.iml",
      "plugins/bazel/aspect-sdk/intellij.libraries.bazel.aspect.sdk.iml",
    ])
  })
})

describe("parseRepositories", () => {
  it("returns the repository URLs in file order without a trailing slash", () => {
    deepEqual(parseRepositories(REPOS), [
      "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2",
      "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies",
    ])
  })
})
