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
        applicationInfoModule = "community-resources"
//        brandingResourcePaths = ["$home/branding/MPS"]
        toolsJarRequired = true

        productLayout.mainJarName = "platform.jar"
        productLayout.mainModules = ["community-main"]
        productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES
        productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES +
                ["platform-main",
                 "testFramework",
                 "tests_bootstrap",
                 "execution-openapi",
                 "execution-impl",
//                 "community-resources",
                 "platform-resources",
                 "platform-resources-en",
                 "colorSchemes",
                 "instrumentation-util",
                 "external-system-impl"]

        productLayout.bundledPluginModules = [
                "git4idea", "remote-servers-git", "remote-servers-git-java", "svn4idea", "cvs-plugin",
                "terminal"
                /*, "properties", "ant"*/
        ]

        productLayout.additionalPlatformJars.put("forms_rt.jar", "forms-compiler")
        productLayout.additionalPlatformJars.putAll("util.jar", ["util", "util-rt"])
        productLayout.additionalPlatformJars.putAll("resources.jar", ["resources", "resources-en"])
        productLayout.additionalPlatformJars.
                putAll("javac2.jar", ["javac2", "forms-compiler", "forms_rt", "instrumentation-util", "instrumentation-util-8"])

        productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
            layout.customize {
                withModule("java-runtime", "idea_rt.jar", false)
                withProjectLibrary("Eclipse")
                withProjectLibrary("jgoodies-common")
                withProjectLibrary("jgoodies-looks")
                withProjectLibrary("commons-net")
                withProjectLibrary("JUnit4")
                withProjectLibrary("http-client-3.1")
                withProjectLibrary("pty4j") // for terminal plugin
                withProjectLibrary("purejavacomm") // for terminal plugin
                withoutProjectLibrary("Ant")
                withoutProjectLibrary("Gradle")
                withoutProjectLibrary("com.twelvemonkeys.imageio:imageio-tiff:3.2.1")
                excludeFromModule("resources", "META-INF/IdeaPlugin.xml")
                excludeFromModule("resources", "componentSets/*")
                excludeFromModule("resources", "ProductivityFeaturesRegistry.xml")
//                excludeFromModule("community-resources", "idea")
//                excludeFromModule("community-resources", "lafs")
//                excludeFromModule("community-resources", "lafs")
                excludeFromModule("platform-resources", "META-INF/LangExtensions.xml")
                excludeFromModule("platform-resources", "META-INF/PlatformLangPlugin.xml")
                excludeFromModule("platform-resources", "META-INF/PlatformLangXmlPlugin.xml")
                excludeFromModule("platform-resources", "META-INF/XmlPlugin.xml")
                excludeFromModule("platform-resources", "META-INF/XmlActions.xml")
                excludeFromModule("platform-resources", "idea/PlatformActions.xml")
                excludeFromModule("platform-resources-en", "messages/FeatureStatisticsBundle.properties")
            }
        } as Consumer<PlatformLayout>

        modulesToCompileTests = ["jps-builders", "jps-model-tests", "jps-serialization-tests"]

        buildSourcesArchive = true
    }

    @Override
    void copyAdditionalFiles(final BuildContext context, final String targetDirectory) {
        super.copyAdditionalFiles(context, targetDirectory)
        context.ant.copy(todir: "$targetDirectory/lib/ant") {
            fileset(dir: "$context.paths.communityHome/lib/ant")
        }

        // for terminal plugin
        context.ant.copy(todir: "$targetDirectory/lib/libpty/") {
            fileset(dir: "$context.paths.communityHome/lib/libpty/")
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