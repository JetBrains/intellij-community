@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.intUi.intUiDecoratedWindow)
    implementation(projects.intUi.intUiStandalone)
    implementation(projects.markdown.core)
    implementation(projects.markdown.extensions.autolink)
    implementation(projects.markdown.extensions.gfmAlerts)
    implementation(projects.markdown.extensions.gfmStrikethrough)
    implementation(projects.markdown.extensions.frontMatter)
    implementation(projects.markdown.extensions.gfmTables)
    implementation(projects.markdown.extensions.images)
    implementation(projects.markdown.intUiStandaloneStyling)
    implementation(projects.samples.showcase)

    implementation(compose.components.resources)
    implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }

    implementation(libs.jbr.api)
    implementation(libs.intellijPlatform.icons)
    implementation(libs.kotlin.reflect)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}

val jdkLevel = project.property("jdk.level") as String

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.samples.standalone.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel Sample"
            packageVersion = "1.0"
            description = "Jewel Sample Application"
            vendor = "JetBrains"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = "Jewel Sample"
                bundleID = "org.jetbrains.jewel.sample.standalone"
                iconFile = file("icons/jewel.icns")
            }
        }
    }
}

tasks {
    withType<JavaExec> {
        // afterEvaluate is needed because the Compose Gradle Plugin
        // register the task in the afterEvaluate block
        afterEvaluate {
            // The sample needs the UI fixes in the JetBrains Runtime. Compilation works with any JDK.
            javaLauncher =
                project.javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(jdkLevel)
                    vendor = JvmVendorSpec.JETBRAINS
                }
            // Resolve the launcher only when the task runs, so a build that does not run the sample needs no JBR.
            doFirst { setExecutable(javaLauncher.get().executablePath.asFile.absolutePath) }
        }
        jvmArgs("-Dcompose.interop.blending=true")
    }
}
