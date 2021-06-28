package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.BuildTasksImpl
import org.jetbrains.intellij.build.impl.PlatformLayout

import java.nio.file.Paths
import java.util.function.Consumer

/**
 * @author victor
 */
class MPSProperties extends JetBrainsProductProperties {
    MPSProperties(String home) {
        baseFileName = "mps"
        productCode = "MPS"
        customProductCode = productCode
        platformPrefix = "Idea"
        applicationInfoModule = "intellij.idea.community.resources"
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
                "intellij.java.compiler.instrumentationUtil",
                "intellij.platform.externalSystem.impl"
        ]
        productLayout.additionalPlatformJars.put(BaseLayout.PLATFORM_JAR, "intellij.idea.community.resources")

        productLayout.additionalPlatformJars.
                putAll("javac2.jar", ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt", "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8"])
        productLayout.additionalPlatformJars.put("forms_rt.jar", "intellij.java.guiForms.compiler")
        productLayout.additionalPlatformJars.putAll("util.jar", ["intellij.platform.util", "intellij.platform.util.rt"])

        productLayout.bundledPluginModules += [
                "intellij.java.plugin",
                "intellij.java.ide.customization",
                "intellij.copyright",
                "intellij.properties",
                "intellij.properties.resource.bundle.editor",
                "intellij.terminal",
                "intellij.emojipicker",
                "intellij.settingsRepository",
                "intellij.tasks.core",
                "intellij.vcs.git",
                "intellij.vcs.svn",
                "intellij.vcs.github",
                "intellij.ant",
                "intellij.sh",
                "intellij.vcs.changeReminder",
                "intellij.markdown",
                "intellij.grazie"
        ]
        productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
        productLayout.buildAllCompatiblePlugins = false
        productLayout.compatiblePluginsToIgnore = ["intellij.java.plugin"]
        productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.stream().map({ pluginLayout ->
            // This plugins are part of COMMUNITY_REPOSITORY_PLUGINS, but with OS restriction
            // We make OS specific builds later so build both ignoring restriction
            if (pluginLayout.mainModule == "intellij.laf.macos" || pluginLayout.mainModule == "intellij.laf.win10") {
                pluginLayout.bundlingRestrictions.supportedOs = OsFamily.ALL
            }
            return pluginLayout
        }).collect() + [
                JavaPluginLayout.javaPlugin()
        ]

        productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
            layout.customize {
                for (String name : BaseIdeaProperties.JAVA_IDE_API_MODULES) {
                    withModule(name)
                }
                for (String name : BaseIdeaProperties.JAVA_IDE_IMPLEMENTATION_MODULES) {
                    withModule(name)
                }
                excludeFromModule("intellij.platform.testFramework", "mockito-extensions/**")

                withModule("intellij.platform.coverage", productLayout.mainJarName)

                withModule("intellij.java.rt", "idea_rt.jar", null)
                withProjectLibrary("Eclipse")
                withProjectLibrary("JUnit4")
                withProjectLibrary("http-client-3.1")
                withProjectLibrary("pty4j") // for terminal plugin
                withoutProjectLibrary("Ant")
                withoutProjectLibrary("Gradle")
            }
        } as Consumer<PlatformLayout>

        additionalModulesToCompile = ["intellij.tools.jps.build.standalone"]
        modulesToCompileTests = ["intellij.platform.jps.build", "intellij.platform.jps.model.tests", "intellij.platform.jps.model.serialization.tests"]

        buildSourcesArchive = true
    }

    @Override
    void copyAdditionalFiles(final BuildContext context, final String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.copy(todir: "$targetDirectory/lib/ant") {
            fileset(dir: "$context.paths.communityHome/lib/ant")
        }

        // copy binaries
        context.ant.copy(todir: "$targetDirectory/bin/linux/") {
            fileset(dir: "$context.paths.communityHome/bin/linux/")
        }
        context.ant.copy(todir: "$targetDirectory/bin/mac/") {
            fileset(dir: "$context.paths.communityHome/bin/mac/", excludes: "*.sh")
        }
        context.ant.copy(todir: "$targetDirectory/bin/win/") {
            fileset(dir: "$context.paths.communityHome/bin/win/")
        }

        // copy mac executable
        context.ant.copy(file: "$context.paths.communityHome/platform/build-scripts/resources/mac/Contents/MacOS/executable",
                tofile: "$targetDirectory/build/resources/mps")

        // copy windows append.bat
        context.ant.copy(file: "$context.paths.communityHome/platform/build-scripts/resources/win/scripts/append.bat",
                todir: "$targetDirectory/bin/win/")

        // copy jre version
        context.ant.copy(file: "$context.paths.communityHome/build/dependencies/gradle.properties",
                todir: "$targetDirectory/build/dependencies/")

        //for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
        context.ant.move(file: "$targetDirectory/lib/annotations-java5.jar", tofile: "$targetDirectory/lib/annotations.jar")

        // scripts needed for mac signing
        context.ant.copy(todir: "$targetDirectory/build/tools/mac/scripts/") {
            fileset(dir: "$context.paths.communityHome/platform/build-scripts/tools/mac/scripts/")
        }

        BuildTasksImpl.generateBuildTxt(context, Paths.get("$targetDirectory/lib"))
    }

    @Override
    String getBaseArtifactName(final ApplicationInfoProperties applicationInfo, final String buildNumber) {
        return "platform"
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