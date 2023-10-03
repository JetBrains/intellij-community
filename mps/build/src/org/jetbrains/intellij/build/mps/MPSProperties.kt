package org.jetbrains.intellij.build.mps

import org.jetbrains.intellij.build.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PlatformLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val javaCompiler: (PlatformLayout, BuildContext) -> Unit = { layout, _ ->
    for (name in listOf(
        "intellij.java.compiler.antTasks",
        "intellij.java.guiForms.compiler",
        "intellij.java.compiler.instrumentationUtil",
        "intellij.java.compiler.instrumentationUtil.java8"
    )) {
        layout.withModule(name, "javac2.jar")
    }
}

class MPSProperties : JetBrainsProductProperties() {

    init {
        platformPrefix = "Idea"
        applicationInfoModule = "intellij.mps.resources"
        toolsJarRequired = true
        scrambleMainJar = false
        /* main module for JetBrains Client isn't available in the intellij-community project,
           so this property is set only when PyCharm Community is built from the intellij-ultimate project. */
        embeddedJetBrainsClientMainModule = null

        productLayout.mainModules = listOf("intellij.idea.community.main")

        productLayout.productImplementationModules = listOf(
            "intellij.platform.main",
            "intellij.idea.community.customization",
            "intellij.java.ide.resources",
            "intellij.xml.dom",
            "intellij.platform.coverage"        // layout.withModule("intellij.platform.coverage", productLayout.mainJarName)
        )

        productLayout.addPlatformSpec(javaCompiler)

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
        productLayout.bundledPluginModules.add("intellij.laf.macos")
        productLayout.bundledPluginModules.add("intellij.laf.win10")

        productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
        productLayout.buildAllCompatiblePlugins = false
        productLayout.compatiblePluginsToIgnore = persistentListOf("intellij.java.plugin")

        val pluginLayouts = productLayout.pluginLayouts + JavaPluginLayout.javaPlugin()
        productLayout.pluginLayouts = pluginLayouts.toPersistentList()

        productLayout.addPlatformSpec { layout, _ ->

            for (moduleName in listOf("intellij.platform.testFramework", "intellij.platform.testFramework.common", "intellij.java.testFramework", "intellij.platform.testFramework.core")) {
                if (!productLayout.productApiModules.contains(moduleName)) {
                    layout.withModule(moduleName, "testFramework.jar")
                }
            }

            layout.withModule("intellij.tools.testsBootstrap")

            layout.excludeFromModule("intellij.platform.testFramework", "mockito-extensions/**")

            layout.withModule("intellij.java.rt", "idea_rt.jar")
            layout.withProjectLibrary("Eclipse", LibraryPackMode.MERGED)
            layout.withProjectLibrary("JUnit4", LibraryPackMode.STANDALONE_MERGED)
            layout.withProjectLibrary("http-client", LibraryPackMode.MERGED)
            layout.withProjectLibrary("pty4j", LibraryPackMode.STANDALONE_MERGED) // for terminal plugin
            layout.withoutProjectLibrary("Ant")
            layout.withoutProjectLibrary("Gradle")
        }

        additionalModulesToCompile = persistentListOf("intellij.tools.jps.build.standalone")
        modulesToCompileTests = persistentListOf(
            "intellij.platform.jps.build",
            "intellij.platform.jps.build.tests",
            "intellij.platform.jps.model.tests",
            "intellij.platform.jps.model.serialization.tests")

        buildSourcesArchive = true
    }

    override suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
        val communityHome = context.paths.communityHome
        FileSet(Path.of("$communityHome/lib/ant")).includeAll().copyToDir(Path.of("$targetDirectory/lib/ant"))

        // copy binaries
        FileSet(Path.of("$communityHome/bin/linux/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/linux/"))
        FileSet(Path.of("$communityHome/bin/mac/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/mac/"))
        FileSet(Path.of("$communityHome/bin/win/")).includeAll().copyToDir(Path.of("$targetDirectory/bin/win/"))

        // copy mac executable
        Files.createDirectories(Path.of("$targetDirectory/build/resources"))
        Files.copy(
            Path.of("$communityHome/platform/build-scripts/resources/mac/Contents/MacOS/executable"),
            Path.of("$targetDirectory/build/resources/mps"),
            StandardCopyOption.COPY_ATTRIBUTES)

        // copy jre version
        FileSet(Path.of("$communityHome/build/dependencies")).include("dependencies.properties").copyToDir(Path.of("$targetDirectory/build/dependencies/"))

        //for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
        File("$targetDirectory/lib/annotations-java5.jar").renameTo(File("$targetDirectory/lib/annotations.jar"))

        // scripts needed for mac signing
        FileSet(Path.of("$communityHome/platform/build-scripts/tools/mac/scripts/")).includeAll().copyToDir(Path.of("$targetDirectory/build/tools/mac/scripts/"))

        generateBuildTxt(context, Path.of("$targetDirectory/lib"))
    }

    private fun generateBuildTxt(context: BuildContext, targetDirectory: Path) {
        Files.writeString(targetDirectory.resolve("build.txt"), context.fullBuildNumber)
    }

    override val baseFileName: String
        get() = "mps"

    override val customProductCode: String
        get() = "MPS"

    override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
        return "MPS${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
    }

    override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "platform"

    override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer? {
        return null
    }

    override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer? {
        return null
    }

    override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer? {
        return null
    }
}