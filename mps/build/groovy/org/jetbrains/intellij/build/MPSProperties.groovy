package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PlatformLayout

import java.util.function.Consumer

/**
 * @author victor
 */
class MPSProperties extends ProductProperties {
    MPSProperties(String home) {
        baseFileName = "mps"
        productCode = "MPS"
        platformPrefix = "Idea"
        applicationInfoModule = "intellij.idea.community.resources"
        toolsJarRequired = true

        productLayout.mainJarName = "platform.jar"
        productLayout.mainModules = ["intellij.idea.community.main"]

        productLayout.productApiModules = BaseIdeaProperties.JAVA_IDE_API_MODULES + [
                "intellij.java.execution"
        ]
        productLayout.productImplementationModules = BaseIdeaProperties.JAVA_IDE_IMPLEMENTATION_MODULES + [
                "intellij.platform.main",
                "intellij.java.execution.impl",
                "intellij.java.compiler.instrumentationUtil",
                "intellij.platform.externalSystem.impl"
        ]

        productLayout.additionalPlatformJars.put("resources.jar", "intellij.java.ide.resources")
        productLayout.additionalPlatformJars.
                putAll("javac2.jar", ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt", "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8", "intellij.java.jps.javacRefScanner8"])
        productLayout.additionalPlatformJars.put("forms_rt.jar", "intellij.java.guiForms.compiler")
        productLayout.additionalPlatformJars.putAll("util.jar", ["intellij.platform.util", "intellij.platform.util.rt"])

        productLayout.bundledPluginModules = [
                "intellij.java.plugin",
                "intellij.terminal",
                "intellij.vcs.git",
                "intellij.vcs.svn",
                "intellij.vcs.github",
                "intellij.java.coverage"
        ]
        productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
        productLayout.compatiblePluginsToIgnore = ["intellij.java.plugin"]
        productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
                JavaPluginLayout.javaPlugin(false)
        ]

        productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
            layout.customize {
                withModule("intellij.platform.coverage", productLayout.mainJarName)

                withModule("intellij.java.rt", "idea_rt.jar", null)
                withProjectLibrary("Eclipse")
//                withProjectLibrary("jgoodies-common")
//                withProjectLibrary("commons-net")
                withProjectLibrary("JUnit4")
                withProjectLibrary("http-client-3.1")
                withProjectLibrary("pty4j") // for terminal plugin
                withoutProjectLibrary("Ant")
                withoutProjectLibrary("Gradle")
                excludeFromModule("intellij.java.resources", "componentSets/*")
//                excludeFromModule("community-resources", "idea")
//                excludeFromModule("community-resources", "lafs")
//                excludeFromModule("community-resources", "lafs")
                excludeFromModule("intellij.platform.resources", "META-INF/LangExtensions.xml")
                excludeFromModule("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
                excludeFromModule("intellij.platform.resources", "META-INF/PlatformLangXmlPlugin.xml")
                excludeFromModule("intellij.platform.resources", "META-INF/XmlPlugin.xml")
                excludeFromModule("intellij.platform.resources", "META-INF/XmlActions.xml")
                excludeFromModule("intellij.platform.resources", "idea/PlatformActions.xml")
                excludeFromModule("intellij.platform.resources.en", "messages/FeatureStatisticsBundle.properties")
                //Removing Idea Tips & Tricks
                excludeFromModule("intellij.java.ide.resources", "ProductivityFeaturesRegistry.xml")
                excludeFromModule("intellij.java.resources.en", "tips/*")
                excludeFromModule("intellij.platform.resources.en", "tips/*")
                excludeFromModule("intellij.platform.remoteServers.impl", "tips/*")
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
            fileset(dir: "$context.paths.communityHome/bin/mac/",
                    excludes: "*.sh")
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