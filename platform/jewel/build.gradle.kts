import java.io.ByteArrayOutputStream
import org.jetbrains.jewel.buildlogic.demodata.AndroidStudioReleasesGeneratorTask
import org.jetbrains.jewel.buildlogic.demodata.StudioVersionsGenerationExtension

plugins {
    alias(libs.plugins.composeDesktop) apply false
    alias(libs.plugins.compose.compiler) apply false
    `jewel-linting`
    `android-studio-releases-generator`
}

// The Android Studio releases sample lives in the DevKit plugin (a JPS module, not a Gradle project)
// since IJPL-174837 moved it out of platform/jewel/samples/ide-plugin. The generator still lives here,
// so it is pointed at the DevKit source tree on demand. Run with:
//     ./gradlew generateAndroidStudioReleasesList
private val devkitComposeDir = layout.projectDirectory.dir("../../plugins/devkit/intellij.devkit.compose")

extensions.configure<StudioVersionsGenerationExtension> {
    outputClassName = "com.intellij.devkit.compose.demo.releasessample.AndroidStudioReleases"
    modelPackage = "com.intellij.devkit.compose.demo.releasessample"
    // DevKit follows the IntelliJ Platform code style, which indents by two spaces.
    indent = "  "
    targetDir = devkitComposeDir.dir("src")
    resourcesDirs = setOf(devkitComposeDir.dir("resources").asFile)
}

tasks.named<AndroidStudioReleasesGeneratorTask>("generateAndroidStudioReleasesList") {
    // DevKit's source tree does not mirror the package structure (src/demo/releasessample, not
    // src/com/intellij/devkit/compose/demo/releasessample), so the derived path needs overriding.
    outputFile = devkitComposeDir.file("src/demo/releasessample/AndroidStudioReleases.kt")
}

tasks {
    register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }

    wrapper { distributionType = Wrapper.DistributionType.ALL }
}
