// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.BuildHelper
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType

import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.UnaryOperator
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
final class KotlinPluginBuilder {
  /**
   * Module which contains META-INF/plugin.xml
   */
  public static String MAIN_KOTLIN_PLUGIN_MODULE = "kotlin.plugin"

  private final String communityHome
  private final String home
  private final ProductProperties properties

  @SuppressWarnings('SpellCheckingInspection')
  public static final List<String> MODULES = List.of(
    "kotlin.core",
    "kotlin.idea",
    "kotlin.fir.frontend-independent",
    "kotlin.line-indent-provider",
    "kotlin.jvm",
    "kotlin.refIndex",
    "kotlin.compiler-plugins.parcelize.common",
    "kotlin.compiler-plugins.parcelize.gradle",
    "kotlin.compiler-plugins.allopen.common",
    "kotlin.compiler-plugins.allopen.gradle",
    "kotlin.compiler-plugins.allopen.maven",
    "kotlin.compiler-plugins.compiler-plugin-support.common",
    "kotlin.compiler-plugins.compiler-plugin-support.gradle",
    "kotlin.compiler-plugins.compiler-plugin-support.maven",
    "kotlin.compiler-plugins.kapt",
    "kotlin.compiler-plugins.kotlinx-serialization.common",
    "kotlin.compiler-plugins.kotlinx-serialization.gradle",
    "kotlin.compiler-plugins.kotlinx-serialization.maven",
    "kotlin.compiler-plugins.noarg.common",
    "kotlin.compiler-plugins.noarg.gradle",
    "kotlin.compiler-plugins.noarg.maven",
    "kotlin.compiler-plugins.sam-with-receiver.common",
    "kotlin.compiler-plugins.sam-with-receiver.gradle",
    "kotlin.compiler-plugins.sam-with-receiver.maven",
    "kotlin.compiler-plugins.lombok.gradle",
    "kotlin.compiler-plugins.lombok.maven",
    "kotlin.compiler-plugins.scripting",
    "kotlin.compiler-plugins.android-extensions-stubs",
    "kotlin.jvm-run-configurations",
    "kotlin.maven",
    "kotlin.gradle.gradle-tooling",
    "kotlin.gradle.gradle-idea",
    "kotlin.gradle.gradle-java",
    "kotlin.gradle.gradle-native",
    "kotlin.native",
    "kotlin.grazie",
    "kotlin.junit",
    "kotlin.testng",
    "kotlin.formatter",
    "kotlin.repl",
    "kotlin.git",
    "kotlin.injection",
    "kotlin.scripting",
    "kotlin.coverage",
    "kotlin.ml-completion",
    "kotlin.groovy",
    "kotlin.copyright",
    "kotlin.spellchecker",
    "kotlin.jvm-decompiler",
    "kotlin.properties",
    "kotlin.j2k.services",
    "kotlin.j2k.idea",
    "kotlin.j2k.old",
    "kotlin.j2k.new",
    "kotlin.project-wizard.cli",
    "kotlin.project-wizard.core",
    "kotlin.project-wizard.idea",
    "kotlin.project-wizard.maven",
    "kotlin.project-wizard.gradle",
    "kotlin.project-wizard-compose",
    "kotlin.jvm-debugger.util",
    "kotlin.jvm-debugger.core",
    "kotlin.jvm-debugger.evaluation",
    "kotlin.jvm-debugger.coroutines",
    "kotlin.jvm-debugger.sequence",
    "kotlin.jvm-debugger.eval4j",
    "kotlin.uast.uast-kotlin-base",
    "kotlin.uast.uast-kotlin",
    "kotlin.uast.uast-kotlin-idea-base",
    "kotlin.uast.uast-kotlin-idea",
    "kotlin.i18n",
    "kotlin.project-model",
    "kotlin.features-trainer",
    )

  @SuppressWarnings('SpellCheckingInspection')
  private static final List<String> LIBRARIES = List.of(
    "kotlinc.android-extensions-compiler-plugin",
    "kotlinc.allopen-compiler-plugin",
    "kotlinc.noarg-compiler-plugin",
    "kotlinc.sam-with-receiver-compiler-plugin",
    "kotlinc.kotlinx-serialization-compiler-plugin",
    "kotlinc.parcelize-compiler-plugin",
    "kotlin-script-runtime",
    "kotlinc.kotlin-scripting-compiler",
    "kotlinc.kotlin-scripting-compiler-impl",
    "kotlinc.kotlin-scripting-common",
    "kotlinc.kotlin-scripting-jvm",
    "kotlinc.kotlin-gradle-statistics",
    "kotlinc.lombok-compiler-plugin"
  )

  KotlinPluginBuilder(String communityHome, String home, ProductProperties properties) {
    this.communityHome = communityHome
    this.home = home
    this.properties = properties
  }

  static PluginLayout kotlinPlugin() {
    return kotlinPlugin(KotlinPluginKind.valueOf(System.getProperty("kotlin.plugin.kind", "IJ")))
  }

  static PluginLayout kotlinPlugin(KotlinPluginKind kind) {
    return PluginLayout.plugin(MAIN_KOTLIN_PLUGIN_MODULE) {
      switch (kind) {
        default:
          directoryName = "Kotlin"
          mainJarName = "kotlin-plugin.jar"
      }

      boolean isUltimate
      try {
        Class.forName("org.jetbrains.intellij.build.IdeaUltimateProperties")
        isUltimate = true
      }
      catch (ClassNotFoundException ignored) {
        isUltimate = false
      }

      for (String moduleName : MODULES) {
        withModule(moduleName)
      }
      for (String library : LIBRARIES) {
        withProjectLibraryUnpackedIntoJar(library, mainJarName)
      }

      if (isUltimate && kind == KotlinPluginKind.IJ) {
        withModule("kotlin-ultimate.common-native")
        //noinspection SpellCheckingInspection
        withModule("kotlin-ultimate.javascript.debugger")
        withModule("kotlin-ultimate.javascript.nodeJs")
        withModule("kotlin-ultimate.ultimate-plugin")
        withModule("kotlin-ultimate.ultimate-native")
      }

      String kotlincKotlinCompiler = "kotlinc.kotlin-compiler"
      withProjectLibrary(kotlincKotlinCompiler, ProjectLibraryData.PackMode.STANDALONE_MERGED)

      withPatch(new BiConsumer<ModuleOutputPatcher, BuildContext>() {
        @Override
        void accept(ModuleOutputPatcher patcher, BuildContext context) {
          JpsLibrary library = context.project.libraryCollection.findLibrary(kotlincKotlinCompiler)
          List<File> jars = library.getFiles(JpsOrderRootType.COMPILED)
          if (jars.size() != 1) {
            throw new IllegalStateException("$kotlincKotlinCompiler is expected to have only one jar")
          }

          BuildHelper.getInstance(context).consumeDataByPrefix
            .invokeWithArguments(jars[0].toPath(), "META-INF/extensions/", new BiConsumer<String, byte[]>() {
              @Override
              void accept(String name, byte[] data) {
                patcher.patchModuleOutput(MAIN_KOTLIN_PLUGIN_MODULE, name, data)
              }
            })
        }
      })

      withModule("kotlin.common", "kotlin-common.jar")

      withProjectLibrary("kotlinc.kotlin-jps-plugin-classpath", "jps/kotlin-jps-plugin.jar")
      withProjectLibrary("kotlinc.kotlin-reflect", "kotlinc-lib.jar")
      withProjectLibrary("kotlinc.kotlin-stdlib", "kotlinc-lib.jar")
      withProjectLibrary("kotlinc.kotlin-jps-common")
      //noinspection SpellCheckingInspection
      withProjectLibrary("javaslang", ProjectLibraryData.PackMode.STANDALONE_MERGED)
      withProjectLibrary("kotlinx-collections-immutable-jvm", ProjectLibraryData.PackMode.STANDALONE_MERGED)
      withProjectLibrary("javax-inject", ProjectLibraryData.PackMode.STANDALONE_MERGED)
      withProjectLibrary("kotlinx-coroutines-jdk8", "kotlinc-lib.jar")
      withProjectLibrary("completion-ranking-kotlin")

      withGeneratedResources(new BiConsumer<Path, BuildContext>() {
        @Override
        void accept(Path targetDir, BuildContext context) {
          String distLibName = "kotlinc.kotlin-dist"
          JpsLibrary library = context.project.libraryCollection.findLibrary(distLibName)
          List<File> jars = library.getFiles(JpsOrderRootType.COMPILED)
          if (jars.size() != 1) {
            throw new IllegalStateException("$distLibName is expected to have only one jar")
          }
          new Decompressor.Zip(jars[0]).extract(targetDir.resolve("kotlinc"))
        }
      })

      withCustomVersion(new PluginLayout.VersionEvaluator() {
        @Override
        String evaluate(Path pluginXml, String buildNumber, BuildContext context) {
          Matcher ijBuildNumber = Pattern.compile("^(\\d+)\\.([\\d.]+|SNAPSHOT.*)\$").matcher(buildNumber)
          if (ijBuildNumber.matches()) {
            String major = ijBuildNumber.group(1)
            String minor = ijBuildNumber.group(2)
            String kotlinVersion = context.project.libraryCollection.libraries
              .find { it.name.startsWith("kotlinc.") && it.type instanceof JpsRepositoryLibraryType }
              ?.asTyped(JpsRepositoryLibraryType.INSTANCE)
              ?.properties?.data?.version
            if (kotlinVersion == null) {
              throw new IllegalStateException("Can't determine Kotlin compiler version")
            }
            String version = "${major}-${kotlinVersion}-${kind}${minor}"
            context.messages.info("version: $version")
            return version
          }
          // Build number isn't recognized as IJ build number then it means build
          // number must be plain Kotlin plugin version (build configuration in kt-branch)
          if (buildNumber.contains("IJ")) {
            String version = buildNumber.replace("IJ", kind.toString())
            context.messages.info("Kotlin plugin IJ version: $version")
            return version
          }

          throw new IllegalStateException("Can't parse build number: $buildNumber")
        }
      })

      withPluginXmlPatcher(new UnaryOperator<String>() {
        @Override
        String apply(String text) {
          String sinceBuild = System.getProperty("kotlin.plugin.since")
          String untilBuild = System.getProperty("kotlin.plugin.until")

          if (sinceBuild != null && untilBuild != null) {
            // In kt-branches we have own since and until versions
            text = replace(text, "<idea-version.*?\\/>", "<idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"/>")
          }

          switch (kind) {
            case KotlinPluginKind.IJ:
              //noinspection SpellCheckingInspection
              text = replace(
                text,
                "<!-- IJ/AS-INCOMPATIBLE-PLACEHOLDER -->",
                "<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>"
              )
              break
            case KotlinPluginKind.AS:
              //noinspection SpellCheckingInspection
              text = replace(
                text,
                "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
                "<plugin id=\"com.intellij.modules.androidstudio\"/>"
              )
              break
            case KotlinPluginKind.AC_KMM:
              text = replace(text, "<plugin id=\"com.intellij.java\"/>", "<plugin id=\"com.intellij.kotlinNative.platformDeps\"/>")
              break
            default:
              throw new IllegalStateException("Unknown kind = $kind")
          }

          return text
        }
      })

      if (kind == KotlinPluginKind.IJ && isUltimate) {
        // TODO KTIJ-11539 change to `System.getenv("TEAMCITY_VERSION") == null` later but make sure
        //  that `IdeaUltimateBuildTest.testBuild` passes on TeamCity
        boolean skipIfDoesntExist = true

        // Use 'DownloadAppCodeDependencies' run configuration to download LLDBFrontend
        withBin("../CIDR/cidr-debugger/bin/lldb/linux/bin/LLDBFrontend", "bin/linux", skipIfDoesntExist)
        withBin("../CIDR/cidr-debugger/bin/lldb/mac/LLDBFrontend", "bin/macos", skipIfDoesntExist)
        withBin("../CIDR/cidr-debugger/bin/lldb/win/x64/bin/LLDBFrontend.exe", "bin/windows", skipIfDoesntExist)

        withBin("../mobile-ide/common-native/scripts", "scripts")
      }
    }
  }

  private static String replace(String oldText, String regex, String newText) {
    String result = oldText.replaceFirst(regex, newText)
    if (result == oldText && /* Update IDE from Sources */ !oldText.contains(newText)) {
      throw new IllegalStateException("Cannot find '$regex' in '$oldText'")
    }
    return result
  }

  def build() {
    BuildContext buildContext = BuildContext.createContext(communityHome, home, properties)
    BuildTasks.create(buildContext).buildNonBundledPlugins([MAIN_KOTLIN_PLUGIN_MODULE])
  }

  enum KotlinPluginKind {
    IJ, AS, MI,
    AC_KMM{ // AppCode KMM plugin
      @Override
      String toString() {
        return "AC"
      }
    }
  }
}
