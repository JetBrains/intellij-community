package org.jetbrains.intellij.build

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PlatformLayout

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.BiConsumer

import static org.jetbrains.intellij.build.impl.DistUtilKt.generateBuildTxt

/**
 * @author victor
 */
@CompileStatic
class MPSProperties extends JetBrainsProductProperties {
    MPSProperties() {
        baseFileName = "mps"
        productCode = "MPS"
        customProductCode = productCode
        platformPrefix = "Idea"
        applicationInfoModule = "intellij.mps.resources"
        toolsJarRequired = true
        scrambleMainJar = false

        productLayout.mainJarName = "platform.jar"
        productLayout.mainModules = ["intellij.idea.community.main"]

        productLayout.productApiModules = [
                "intellij.java.execution"
        ]
        productLayout.productImplementationModules = [
                "intellij.platform.main",
                "intellij.java.execution.impl",
                "intellij.java.compiler.instrumentationUtil"
        ]
        productLayout.withAdditionalPlatformJar(BaseLayout.APP_JAR, "intellij.idea.community.resources", "intellij.java.ide.resources", "intellij.xml.dom")


        productLayout.withAdditionalPlatformJar("javac2.jar",
                "intellij.java.compiler.antTasks",
                "intellij.java.guiForms.compiler",
                "intellij.java.compiler.instrumentationUtil",
                "intellij.java.compiler.instrumentationUtil.java8")
        productLayout.withAdditionalPlatformJar("util.jar",
                "intellij.platform.util")

        productLayout.bundledPluginModules.add("intellij.java.plugin")
        productLayout.bundledPluginModules.add("intellij.java.ide.customization")
        productLayout.bundledPluginModules.add("intellij.copyright")
        productLayout.bundledPluginModules.add("intellij.properties")
        productLayout.bundledPluginModules.add("intellij.properties.resource.bundle.editor")
        productLayout.bundledPluginModules.add("intellij.terminal")
        productLayout.bundledPluginModules.add("intellij.emojipicker")
        productLayout.bundledPluginModules.add("intellij.settingsRepository")
        productLayout.bundledPluginModules.add("intellij.tasks.core")
        productLayout.bundledPluginModules.add("intellij.vcs.git")
        productLayout.bundledPluginModules.add("intellij.vcs.svn")
        productLayout.bundledPluginModules.add("intellij.vcs.github")
        productLayout.bundledPluginModules.add("intellij.ant")
        productLayout.bundledPluginModules.add("intellij.sh")
        productLayout.bundledPluginModules.add("intellij.vcs.changeReminder")
        productLayout.bundledPluginModules.add("intellij.markdown")
        productLayout.bundledPluginModules.add("intellij.grazie")

        productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
        productLayout.buildAllCompatiblePlugins = false
        productLayout.compatiblePluginsToIgnore = ["intellij.java.plugin"]
        productLayout.allNonTrivialPlugins = (CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.stream().map({ pluginLayout ->
            // This plugins are part of COMMUNITY_REPOSITORY_PLUGINS, but with OS restriction
            // We make OS specific builds later so build both ignoring restriction
            if (pluginLayout.mainModule == "intellij.laf.macos" || pluginLayout.mainModule == "intellij.laf.win10") {
                pluginLayout.bundlingRestrictions = new PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, pluginLayout.bundlingRestrictions.includeInEapOnly)
            }
            return pluginLayout
        }).collect() + [
                JavaPluginLayout.javaPlugin()
        ]).toList()

        var layoutCustomizer = new BiConsumer<PlatformLayout, BuildContext>() {
            @Override
            void accept(PlatformLayout layout, BuildContext buildContext) {
                for (String moduleName : List.of("intellij.java.testFramework", "intellij.platform.testFramework.core")) {
                    if (!productLayout.productApiModules.contains(moduleName)) {
                        layout.withModule(moduleName, "testFramework.jar")
                    }
                }
                for (String name : BaseIdeaProperties.JAVA_IDE_IMPLEMENTATION_MODULES) {
                    layout.withModule(name)
                }
                layout.excludeFromModule("intellij.platform.testFramework", "mockito-extensions/**")

                layout.withModule("intellij.platform.coverage", productLayout.mainJarName)
                layout.withModule("intellij.java.guiForms.rt")

                layout.withModule("intellij.java.rt", "idea_rt.jar")
                layout.withProjectLibrary("Eclipse", LibraryPackMode.MERGED)
                layout.withProjectLibrary("JUnit4", LibraryPackMode.STANDALONE_MERGED)
                layout.withProjectLibrary("http-client-3.1", LibraryPackMode.MERGED)
                layout.withProjectLibrary("pty4j", LibraryPackMode.STANDALONE_MERGED) // for terminal plugin
                layout.withoutProjectLibrary("Ant")
                layout.withoutProjectLibrary("Gradle")
            }
        }
        productLayout.addPlatformCustomizer(layoutCustomizer)

        additionalModulesToCompile = ["intellij.tools.jps.build.standalone"]
        modulesToCompileTests = ["intellij.platform.jps.build", "intellij.platform.jps.model.tests", "intellij.platform.jps.model.serialization.tests"]

        buildSourcesArchive = true
    }

    @Override
    @CompileDynamic
    void copyAdditionalFiles(final BuildContext context, final String targetDirectory) {
        new FileSet(Path.of("$context.paths.communityHome/lib/ant")).includeAll().copyToDir(Path.of("$targetDirectory/lib/ant"))

        // copy binaries
        new FileSet(Path.of("$context.paths.communityHome/bin/linux/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/linux/"))
        new FileSet(Path.of("$context.paths.communityHome/bin/mac/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/mac/"))
        new FileSet(Path.of("$context.paths.communityHome/bin/win/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/win/"))

        // copy mac executable
        Files.createDirectories(Path.of("$targetDirectory/build/resources"))
        Files.copy(
                Path.of("$context.paths.communityHome/platform/build-scripts/resources/mac/Contents/MacOS/executable"),
                Path.of("$targetDirectory/build/resources/mps"),
                StandardCopyOption.COPY_ATTRIBUTES)

        // copy jre version
        new FileSet(Path.of("$context.paths.communityHome/build/dependencies")).include("gradle.properties").copyToDir(Path.of("$targetDirectory/build/dependencies/"))

        //for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
        new File("$targetDirectory/lib/annotations-java5.jar").renameTo(new File("$targetDirectory/lib/annotations.jar"))

        // scripts needed for mac signing
        new FileSet(Path.of("$context.paths.communityHome/platform/build-scripts/tools/mac/scripts/")).includeAll().copyToDir(Path.of("$targetDirectory/build/tools/mac/scripts/"))

        generateBuildTxt(context, Path.of("$targetDirectory/lib"))
    }

    @Override
    String getBaseArtifactName(final ApplicationInfoProperties applicationInfo, final String buildNumber) {
        return "platform"
    }

    @Override
    String getSystemSelector(ApplicationInfoProperties applicationInfo, String buildNumber) {
        return "MPS${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}"
    }

    @Override
    WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
        return null
    }

    @Override
    LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
        return null
    }

    @Override
    MacDistributionCustomizer createMacCustomizer(String projectHome) {
        return null
    }
}