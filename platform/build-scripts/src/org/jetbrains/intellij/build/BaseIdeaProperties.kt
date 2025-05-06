// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder

/**
 * Default bundled plugins for all editions of IntelliJ IDEA.
 * See also [DEFAULT_BUNDLED_PLUGINS].
 */
@Suppress("SpellCheckingInspection")
val IDEA_BUNDLED_PLUGINS: PersistentList<String> = DEFAULT_BUNDLED_PLUGINS + sequenceOf(
  JavaPluginLayout.MAIN_MODULE_NAME,
  "intellij.java.ide.customization",
  "intellij.copyright",
  "intellij.properties",
  "intellij.terminal",
  "intellij.textmate",
  "intellij.editorconfig.plugin",
  "intellij.settingsSync",
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
  "intellij.groovy",
  "intellij.junit",
  "intellij.testng",
  "intellij.java.i18n",
  "intellij.java.byteCodeViewer",
  "intellij.java.coverage",
  "intellij.java.decompiler",
  "intellij.eclipse",
  "intellij.platform.langInjection",
  "intellij.java.debugger.streams",
  "intellij.completionMlRanking",
  "intellij.completionMlRankingModels",
  "intellij.statsCollector",
  "intellij.sh",
  "intellij.markdown",
  "intellij.mcpserver",
  "intellij.webp",
  "intellij.grazie",
  "intellij.featuresTrainer",
  "intellij.searchEverywhereMl",
  "intellij.marketplaceMl",
  "intellij.toml",
  KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE,
  "intellij.keymap.eclipse",
  "intellij.keymap.visualStudio",
  "intellij.keymap.netbeans",
  "intellij.performanceTesting",
  "intellij.turboComplete",
  "intellij.compose.ide.plugin",
  "intellij.findUsagesMl",
)

val CE_CLASS_VERSIONS: Map<String, String> = mapOf(
  "" to "17",
  "lib/idea_rt.jar" to "1.7",
  "lib/forms_rt.jar" to "1.8",
  "lib/annotations.jar" to "1.8",
  "lib/util_rt.jar" to "1.7",
  "lib/util-8.jar" to "1.8",
  "lib/external-system-rt.jar" to "1.8",
  "plugins/java-coverage/lib/java-coverage-rt.jar" to "1.8",
  "plugins/junit/lib/junit-rt.jar" to "1.7",
  "plugins/junit/lib/junit5-rt.jar" to "1.8",
  "plugins/gradle/lib/gradle-tooling-extension-api.jar" to "1.8",
  "plugins/gradle/lib/gradle-tooling-extension-impl.jar" to "1.8",
  "plugins/maven/lib/maven-server.jar" to "1.8",
  "plugins/maven/lib/maven3-server-common.jar" to "1.8",
  "plugins/maven/lib/maven3-server.jar" to "1.8",
  "plugins/maven/lib/artifact-resolver-m31.jar" to "1.8",
  "plugins/java/lib/jshell-frontend.jar" to "9",
  "plugins/java/lib/sa-jdwp" to "",  // ignored
  "plugins/java/lib/rt/debugger-agent.jar" to "1.7",
  "plugins/Groovy/lib/groovy-rt.jar" to "1.7",
  "plugins/Groovy/lib/groovy-constants-rt.jar" to "1.7",
  "plugins/repository-search/lib/maven-model.jar" to "1.8"
)

/**
 * Describes modules to be added to 'testFramework.jar' in the IDE distribution. This JAR was used to compile and run tests in external plugins.
 * Since [#477](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/477) is implemented, it's possible to take test framework JARs from Maven repository,
 * not from the IDE distribution.
 * So this JAR is kept for compatibility only, **please do not add new modules here**.
 * To publish a new test framework module, register it in [MavenArtifactsProperties.additionalModules] for the corresponding IDEs instead.
 */
@ApiStatus.Obsolete
val TEST_FRAMEWORK_LAYOUT_CUSTOMIZER: (PlatformLayout, BuildContext) -> Unit = { layout, _ ->
  for (name in listOf(
    "intellij.platform.testFramework.common",
    "intellij.platform.testFramework.junit5",
    "intellij.platform.testFramework",
    "intellij.platform.testFramework.core",
    "intellij.platform.testFramework.impl",
    "intellij.platform.testFramework.teamCity",
    "intellij.tools.testsBootstrap",
  )) {
    layout.withModule(name, TEST_FRAMEWORK_JAR)
  }
  layout.withoutProjectLibrary("JUnit4")
  layout.withoutProjectLibrary("JUnit5")
  layout.withoutProjectLibrary("JUnit5Jupiter")
  @Suppress("SpellCheckingInspection")
  layout.withoutProjectLibrary("opentest4j")
}

val TEST_FRAMEWORK_WITH_JAVA_RT: (PlatformLayout, BuildContext) -> Unit = { layout, context ->
  TEST_FRAMEWORK_LAYOUT_CUSTOMIZER(layout, context)
  layout.withModule("intellij.java.rt", TEST_FRAMEWORK_JAR)
}

/**
 * Base class for all editions of IntelliJ IDEA
 */
abstract class BaseIdeaProperties : JetBrainsProductProperties() {
  init {
    productLayout.addPlatformSpec(TEST_FRAMEWORK_LAYOUT_CUSTOMIZER)
    productLayout.addPlatformSpec { layout, _ ->
      layout.withModule("intellij.java.ide.resources")

      if (!productLayout.productApiModules.contains("intellij.jsp.base")) {
        layout.withModule("intellij.jsp.base")
      }

      for (moduleName in arrayOf(
        "intellij.java.testFramework",
        "intellij.java.testFramework.shared",
        "intellij.platform.debugger.testFramework",
        "intellij.platform.uast.testFramework",
      )) {
        if (!productLayout.productApiModules.contains(moduleName) && !productLayout.productImplementationModules.contains(moduleName)) {
          layout.withModule(moduleName, TEST_FRAMEWORK_JAR)
        }
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

    productLayout.compatiblePluginsToIgnore = persistentListOf(
      JavaPluginLayout.MAIN_MODULE_NAME,
    )
    modulesToCompileTests = persistentListOf("intellij.platform.jps.build.tests")
  }
}
