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
//        brandingResourcePaths = ["$home/branding/MPS"]
        toolsJarRequired = true

        productLayout.mainJarName = "platform.jar"
        productLayout.mainModules = ["intellij.idea.community.main"]
        productLayout.productApiModules = ["intellij.java.execution"]
        productLayout.productImplementationModules =
                ["intellij.platform.main",
                 "intellij.platform.testFramework",
                 "intellij.tools.testsBootstrap",
                 "intellij.java.execution.impl",
                 "intellij.java.compiler.instrumentationUtil",
                 "intellij.platform.externalSystem.impl"]

        productLayout.bundledPluginModules = [
                "intellij.vcs.git", "intellij.platform.remoteServers.git", "intellij.java.remoteServers.git", "intellij.vcs.svn", "intellij.vcs.github", "intellij.vcs.cvs",
                "intellij.terminal"
                /*, "properties", "ant"*/
        ]

        productLayout.additionalPlatformJars.put("forms_rt.jar", "intellij.java.guiForms.compiler")
        productLayout.additionalPlatformJars.putAll("util.jar", ["intellij.platform.util", "intellij.platform.util.rt"])
        productLayout.additionalPlatformJars.putAll("resources.jar", ["intellij.java.resources", "intellij.java.resources.en"])
        productLayout.additionalPlatformJars.
                putAll("javac2.jar", ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt", "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8"])

        // Copied from BaseIdeaProperties
        def JAVA_API_JAR = "java-api.jar"
        def JAVA_IMPL_JAR = "java-impl.jar"
        productLayout.additionalPlatformJars.putAll(JAVA_API_JAR, [])
        productLayout.additionalPlatformJars.putAll(JAVA_IMPL_JAR, [])

        productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
            layout.customize {

                // Copied from BaseIdeaProperties
                def JAVA_RESOURCES_JAR = "java_resources_en.jar"
                withModule("intellij.java.analysis", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.jvm.analysis", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.indexing", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.psi", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.jsp.base", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.jsp", JAVA_API_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.platform.uast", JAVA_API_JAR, JAVA_RESOURCES_JAR)

                withModule("intellij.java.analysis.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.jvm.analysis.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.indexing.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.psi.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.jsp.spi", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                withModule("intellij.java.uast", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
                // end of copy from BaseIdeaProperties

                withModule("intellij.java.rt", "idea_rt.jar", null)
                withProjectLibrary("Eclipse")
//                withProjectLibrary("jgoodies-common")
//                withProjectLibrary("commons-net")
                withProjectLibrary("JUnit4")
                withProjectLibrary("http-client-3.1")
                withProjectLibrary("pty4j") // for terminal plugin
                withoutProjectLibrary("Ant")
                withoutProjectLibrary("Gradle")
                excludeFromModule("intellij.java.resources", "META-INF/IdeaPlugin.xml")
                excludeFromModule("intellij.java.resources", "componentSets/*")
                excludeFromModule("intellij.java.resources", "ProductivityFeaturesRegistry.xml")
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
                excludeFromModule("intellij.java.resources", "META-INF/IdeTipsAndTricks.xml")
                excludeFromModule("intellij.java.resources.en", "tips/*")
                excludeFromModule("intellij.platform.resources.en", "tips/*")
                excludeFromModule("intellij.platform.remoteServers.impl", "tips/*")
            }
        } as Consumer<PlatformLayout>

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