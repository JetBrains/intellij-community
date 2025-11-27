// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.productLayout.DEFAULT_BUNDLED_PLUGINS

/**
 * Default bundled plugins for all editions of IntelliJ IDEA.
 * See also [org.jetbrains.intellij.build.productLayout.DEFAULT_BUNDLED_PLUGINS].
 */
val IDEA_BUNDLED_PLUGINS: PersistentList<String> = DEFAULT_BUNDLED_PLUGINS + persistentListOf(
  JavaPluginLayout.MAIN_MODULE_NAME,
  "intellij.java.ide.customization",
  "intellij.copyright",
  "intellij.properties",
  "intellij.terminal",
  "intellij.textmate.plugin",
  "intellij.editorconfig.plugin",
  "intellij.configurationScript",
  "intellij.json",
  "intellij.yaml",
  "intellij.html.tools",
  "intellij.tasks.core",
  "intellij.repository.search",
  "intellij.maven",
  "intellij.gradle",
  "intellij.android.gradle.declarative.lang.ide",
  "intellij.android.gradle.dsl",
  "intellij.gradle.java",
  "intellij.vcs.git",
  "intellij.vcs.git.commit.modal",
  "intellij.vcs.svn",
  "intellij.vcs.hg",
  "intellij.vcs.github",
  "intellij.vcs.gitlab",
  "intellij.groovy",
  "intellij.junit",
  "intellij.testng",
  "intellij.java.i18n",
  "intellij.java.byteCodeViewer",
  "intellij.java.coverage",
  "intellij.java.decompiler",
  "intellij.eclipse",
  "intellij.java.debugger.streams",
  "intellij.sh.plugin",
  "intellij.markdown",
  "intellij.mcpserver",
  "intellij.grazie",
  "intellij.featuresTrainer",
  "intellij.toml",
  KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE,
  "intellij.keymap.eclipse",
  "intellij.keymap.visualStudio",
  "intellij.keymap.netbeans",
  "intellij.performanceTesting",
  "intellij.compose.ide.plugin",
)

val CE_CLASS_VERSIONS: Map<String, String> = mapOf(
  "" to "21",
  "lib/idea_rt.jar" to "1.8",
  "lib/forms_rt.jar" to "1.8",
  "lib/annotations.jar" to "1.8",
  "lib/util_rt.jar" to "1.8",
  "lib/util-8.jar" to "1.8",
  "lib/external-system-rt.jar" to "1.8",
  "plugins/java-coverage/lib/java-coverage-rt.jar" to "1.8",
  "plugins/junit/lib/junit-rt.jar" to "1.8",
  "plugins/junit/lib/junit5-rt.jar" to "1.8",
  "plugins/gradle/lib/gradle-tooling-extension-api.jar" to "1.8",
  "plugins/gradle/lib/gradle-tooling-extension-impl.jar" to "1.8",
  "plugins/maven/lib/maven-server.jar" to "1.8",
  "plugins/maven/lib/maven3-server-common.jar" to "1.8",
  "plugins/maven/lib/maven3-server.jar" to "1.8",
  "plugins/maven/lib/artifact-resolver-m31.jar" to "1.8",
  "plugins/java/lib/sa-jdwp" to "",  // ignored
  "plugins/java/lib/rt/debugger-agent.jar" to "1.7",
  "plugins/Groovy/lib/groovy-rt.jar" to "1.8",
  "plugins/Groovy/lib/groovy-constants-rt.jar" to "1.8",
  "plugins/repository-search/lib/maven-model.jar" to "1.8"
)

fun configurePropertiesForAllEditionsOfIntelliJIdea(properties: JetBrainsProductProperties) {
  properties.productLayout.addPlatformSpec { layout, _ ->
    layout.withModule("intellij.java.ide.resources")

    if (!properties.productLayout.productApiModules.contains("intellij.jsp.base")) {
      layout.withModule("intellij.jsp.base")
    }

    //todo currently intellij.platform.testFramework included into idea.jar depends on this jar so it cannot be moved to java plugin
    layout.withModule("intellij.java.rt", "idea_rt.jar")
    // for compatibility with user projects which refer to IDEA_HOME/lib/annotations.jar
    layout.withProjectLibrary("jetbrains-annotations", "annotations.jar")

    layout.withoutProjectLibrary("Ant")
    // there is a patched version of the org.gradle.api.JavaVersion class placed into the Gradle plugin classpath as "rt" jar
    // to avoid class linkage conflicts "Gradle" library is placed into the 'lib' directory of the Gradle plugin layout so we need to exclude it from the platform layout explicitly
    // TODO should be used as regular project library when the issue will be fixed at the Gradle tooling api side https://github.com/gradle/gradle/issues/8431 and the patched class will be removed
    layout.withoutProjectLibrary("Gradle")

    // this library is placed into a subdirectory of the 'lib' directory in the Android plugin layout, so we need to exclude it from the platform layout explicitly
    layout.withoutProjectLibrary("layoutlib")

    layout.withoutProjectLibrary("jetbrains.qodana.cloud.kotlin.client")
    layout.withoutProjectLibrary("jetbrains.qodana.publisher")
    layout.withoutProjectLibrary("jetbrains.qodana.sarif.converter")
    layout.withoutProjectLibrary("jetbrains.qodana.web.ui")
    layout.withoutProjectLibrary("qodana-sarif")
    // todo it is a quick fix - fix the root cause
    layout.withoutProjectLibrary("assertJ")
    layout.withoutProjectLibrary("hamcrest")
  }

  properties.productLayout.compatiblePluginsToIgnore = persistentListOf(
    JavaPluginLayout.MAIN_MODULE_NAME,
  )
  properties.modulesToCompileTests += persistentListOf("intellij.platform.jps.build.tests")
}
