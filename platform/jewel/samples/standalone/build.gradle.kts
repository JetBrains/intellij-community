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
    implementation(projects.markdown.extensions.gfmAlerts)
    implementation(projects.markdown.extensions.gfmStrikethrough)
    implementation(projects.markdown.extensions.gfmTables)
    implementation(projects.markdown.extensions.autolink)
    implementation(projects.markdown.intUiStandaloneStyling)
    implementation(projects.samples.showcase)

    implementation(compose.components.resources)
    implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }

    implementation(libs.filePicker)
    implementation(libs.intellijPlatform.icons)
    implementation(libs.kotlin.reflect)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}

val jdkLevel = project.property("jdk.level") as String

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(jdkLevel)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

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
            javaLauncher = project.javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jdkLevel) }
            setExecutable(javaLauncher.map { it.executablePath.asFile.absolutePath }.get())
        }
    }
}
