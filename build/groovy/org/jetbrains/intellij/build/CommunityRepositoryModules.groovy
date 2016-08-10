/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */






package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

/**
 * @author nik
 */
@CompileStatic
class CommunityRepositoryModules {
  static List<String> PLATFORM_API_MODULES = [
    "analysis-api",
    "built-in-server-api",
    "core-api",
    "diff-api",
    "dvcs-api",
    "editor-ui-api",
    "external-system-api",
    "indexing-api",
    "jps-model-api",
    "lang-api",
    "lvcs-api",
    "platform-api",
    "projectModel-api",
    "remote-servers-agent-rt",
    "remote-servers-api",
    "usageView",
    "vcs-api-core",
    "vcs-api",
    "vcs-log-api",
    "vcs-log-graph-api",
    "xdebugger-api",
    "xml-analysis-api",
    "xml-openapi",
    "xml-psi-api",
    "xml-structure-view-api"
  ]

  static List<String> PLATFORM_IMPLEMENTATION_MODULES = [
    "analysis-impl",
    "built-in-server",
    "core-impl",
    "credential-store",
    "diff-impl",
    "dvcs-impl",
    "editor-ui-ex",
    "images",
    "indexing-impl",
    "jps-model-impl",
    "jps-model-serialization",
    "json",
    "lang-impl",
    "lvcs-impl",
    "platform-impl",
    "projectModel-impl",
    "protocol-reader-runtime",
    "RegExpSupport",
    "relaxng",
    "remote-servers-impl",
    "script-debugger-backend",
    "script-debugger-ui",
    "smRunner",
    "spellchecker",
    "structure-view-impl",
    "testRunner",
    "vcs-impl",
    "vcs-log-graph",
    "vcs-log-impl",
    "xdebugger-impl",
    "xml-analysis-impl",
    "xml-psi-impl",
    "xml-structure-view-impl",
    "xml",
    "configuration-store-impl",
  ]

  /**
   * Specifies layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  static List<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = [
    plugin("copyright"),
    plugin("java-i18n"),
    plugin("hg4idea"),
    plugin("github"),
    plugin("ant") {
      mainJarName = "antIntegration.jar"
      withModule("ant-jps-plugin")
    },
    plugin("ui-designer") {
      directoryName = "uiDesigner"
      mainJarName = "uiDesigner.jar"
      withJpsModule("ui-designer-jps-plugin")
    },
    plugin("properties") {
      withModule("properties-psi-api", "properties.jar")
      withModule("properties-psi-impl", "properties.jar")
    },
    plugin("git4idea") {
      withModule("git4idea-rt", "git4idea-rt.jar", false)
      withOptionalModule("remote-servers-git")
      withOptionalModule("remote-servers-git-java", "remote-servers-git.jar")
    },
    plugin("svn4idea") {
      withResource("lib/licenses", "lib/licenses")
    },
    plugin("cvs-plugin") {
      directoryName = "cvsIntegration"
      mainJarName = "cvsIntegration.jar"
      withModule("javacvs-src")
      withModule("smartcvs-src")
      withModule("cvs-core", "cvs_util.jar")
    },
    plugin("xpath") {
      withModule("xslt-rt", "rt/xslt-rt.jar")
    },
    plugin("tasks-core") {
      directoryName = "tasks"
      withModule("tasks-api")
      withModule("jira")
      withOptionalModule("tasks-java")
      doNotCreateSeperateJarForLocalizableResources()
    },
    plugin("terminal") {
      withResource("lib/jediterm.in", "lib")
    },
    plugin("editorconfig"),
    plugin("coverage"),
    plugin("yaml"),
    plugin("xslt-debugger") {
      withModule("xslt-debugger-engine")
      withModule("xslt-debugger-engine-impl", "rt/xslt-debugger-engine-impl.jar")
      withModuleLibrary("Saxon-6.5.5", "xslt-debugger-engine-impl", "rt")
      withModuleLibrary("Saxon-9HE", "xslt-debugger-engine-impl", "rt")
      withModuleLibrary("Xalan-2.7.1", "xslt-debugger-engine-impl", "rt")
      //todo[nik] unmark 'lib' directory as source root instead
      excludeFromModule("xslt-debugger-engine-impl", "rmi-stubs.jar")
      excludeFromModule("xslt-debugger-engine-impl", "saxon.jar")
      excludeFromModule("xslt-debugger-engine-impl", "saxon9he.jar")
      excludeFromModule("xslt-debugger-engine-impl", "serializer.jar")
      excludeFromModule("xslt-debugger-engine-impl", "xalan.jar")
    },
    plugin("settings-repository")
  ]
}