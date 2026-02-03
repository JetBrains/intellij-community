package org.jetbrains.intellij.build.mps

import org.jetbrains.intellij.build.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension

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
        platformPrefix = "MPS"
        applicationInfoModule = "intellij.mps.resources"
        scrambleMainJar = false
        /* main module for JetBrains Client isn't available in the intellij-community project,
           so this property is set only when PyCharm Community is built from the intellij-ultimate project. */
        embeddedFrontendRootModule = null
        customCompatibleBuildRange = CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE

        productLayout.productImplementationModules = listOf(
            "intellij.platform.starter",
            "intellij.idea.community.customization",
            "intellij.java.ide.resources",
            "intellij.platform.whatsNew",
        )

        productLayout.addPlatformSpec(javaCompiler)

        productLayout.bundledPluginModules +=
        sequenceOf(
            "intellij.java.plugin",
            "intellij.java.ide.customization",
            "intellij.json",
            "intellij.copyright",
            "intellij.properties",
            "intellij.properties.resource.bundle.editor",
            "intellij.terminal",
            "intellij.tasks.core",
            "intellij.vcs.git",
            "intellij.vcs.svn",
            "intellij.vcs.github",
            "intellij.vcs.git.commit.modal",
            "intellij.ant",
            "intellij.sh.plugin",
            "intellij.markdown",
            "intellij.grazie",
        )

        // A plugin from intellij-community may refer to some additional modules located in the ultimate
        // part of the project, and they should be skipped while building from intellij-community sources
        productLayout.skipUnresolvedContentModules = true
        productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
        productLayout.buildAllCompatiblePlugins = false
        productLayout.compatiblePluginsToIgnore = persistentListOf("intellij.java.plugin")

        val pluginLayouts = productLayout.pluginLayouts + JavaPluginLayout.javaPlugin()
        productLayout.pluginLayouts = pluginLayouts.toPersistentList()

        productLayout.addPlatformSpec { layout, _ ->

            for (moduleName in listOf("intellij.platform.testFramework", "intellij.platform.testFramework.common", "intellij.java.testFramework", "intellij.platform.testFramework.core", "intellij.platform.testFramework.teamCity")) {
                if (!productLayout.productApiModules.contains(moduleName)) {
                    layout.withModule(moduleName, "testFramework.jar")
                }
            }

            // Contains the expanded plugin.xml inside
            layout.withModule("intellij.mps.resources", "mps-resources.zip")

            layout.withModule("intellij.tools.testsBootstrap")

            layout.excludeFromModule("intellij.platform.testFramework", "mockito-extensions/**")

            layout.withModule("intellij.java.rt", "idea_rt.jar")
            layout.withProjectLibrary("Eclipse", LibraryPackMode.MERGED)
            layout.withProjectLibrary("JUnit4", LibraryPackMode.STANDALONE_MERGED)
            layout.withProjectLibrary("http-client", LibraryPackMode.MERGED)
            layout.withoutProjectLibrary("Ant")
            layout.withoutProjectLibrary("Gradle")
            layout.withProjectLibrary("maven-resolver-provider", LibraryPackMode.STANDALONE_MERGED)
        }

        modulesToCompileTests = persistentListOf(
            "intellij.platform.jps.build",
            "intellij.platform.jps.build.tests",
            "intellij.platform.jps.model.tests",
            "intellij.platform.jps.model.serialization.tests")

        buildSourcesArchive = true
    }

    override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
        val communityHome = COMMUNITY_ROOT.communityRoot
        FileSet(Path.of("$communityHome/lib/ant")).includeAll().copyToDir(Path.of("$targetDir/lib/ant"))

        // copy binaries
        FileSet(Path.of("$communityHome/bin/linux/")).includeAll().copyToDir(Path.of("$targetDir/bin/linux/"))
        FileSet(Path.of("$communityHome/bin/mac/")).includeAll().copyToDir(Path.of("$targetDir/bin/mac/"))
        FileSet(Path.of("$communityHome/bin/win/")).includeAll().copyToDir(Path.of("$targetDir/bin/win/"))

        // copy Window restarter
        copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.WINDOWS, JvmArchitecture.x64), Path.of("$targetDir/bin/win/amd64"))
        copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.WINDOWS, JvmArchitecture.aarch64), Path.of("$targetDir/bin/win/aarch64"))

        copyExecutables(context, targetDir)

        // copy jre version
        FileSet(Path.of("$communityHome/build/dependencies")).include("dependencies.properties").copyToDir(Path.of("$targetDir/build/dependencies/"))

        //for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
        File("$targetDir/lib/annotations-java5.jar").renameTo(File("$targetDir/lib/annotations.jar"))

        // scripts needed for mac signing
        FileSet(Path.of("$communityHome/platform/build-scripts/tools/mac/scripts/")).includeAll().copyToDir(Path.of("$targetDir/build/tools/mac/scripts/"))

        generateBuildTxt(context, Path.of("$targetDir/lib"))
    }

    private suspend fun copyExecutables(context: BuildContext, targetDirectory: Path) {
        Files.createDirectories(Path.of("$targetDirectory/build/resources"))
        for (osFamily in OsFamily.entries) {
            for (arch in JvmArchitecture.entries) {
                val (execPath, _) = NativeBinaryDownloader.getLauncher(context, osFamily, arch)
                var ext = execPath.extension
                ext = if (ext.isEmpty()) "" else ".$ext"
                Files.copy(
                    execPath,
                    Path.of("$targetDirectory/build/resources/$baseFileName-${osFamily.dirName}-${arch.fileSuffix}$ext"),
                    StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }

    private fun generateBuildTxt(context: BuildContext, targetDirectory: Path) {
        Files.writeString(targetDirectory.resolve("build.txt"), context.fullBuildNumber)
    }

    override val baseFileName: String
        get() = "mps"

    override val customProductCode: String
        get() = "MPS"

    override fun getProductContentDescriptor(): ProductModulesContentSpec? = null

    override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
        return "MPS${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
    }

    override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "platform"

    override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer {
        return MPSWindowsDistributionCustomizer(MPSBuilder.MPS_HOME)
    }

    override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer? {
        return null
    }

    override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer? {
        return null
    }

    private class MPSWindowsDistributionCustomizer(projectHome: Path) : WindowsDistributionCustomizer() {
        override val fileAssociations: List<String>
            get() = listOf("mps", "mpsr", "model")

        override val associateIpr: Boolean
            get() = false

        init {
            icoPath = projectHome.resolve("build/resources/mps.ico")
            icoPathForEAP = projectHome.resolve("build/resources/mps.ico")
            // The following properties are required by the build script but are only used when building installers
            // We ignore installer artifacts in Platform builds but set these to reasonable values anyway
            installerImagesPath = projectHome.resolve("build/resources")
        }
    }
}