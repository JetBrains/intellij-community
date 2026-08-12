plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

val spectreTest = sourceSets.create("spectreTest") { java.srcDir("src/spectreTest/kotlin") }

dependencies {
    api(projects.intUi.intUiStandalone)

    testImplementation(projects.foundation)
    testImplementation(projects.ui)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    testImplementation(libs.jna.core)

    "spectreTestImplementation"(projects.foundation)
    "spectreTestImplementation"(projects.ui)
    "spectreTestImplementation"(projects.intUi.intUiStandalone)
    "spectreTestImplementation"(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    "spectreTestImplementation"(libs.spectre.core)
    "spectreTestImplementation"(libs.spectre.testing)
    "spectreTestImplementation"(kotlin("test"))
    "spectreTestImplementation"(libs.junit.jupiter)
    "spectreTestImplementation"(libs.kotlinx.coroutines.core)
    "spectreTestRuntimeOnly"(libs.junit.platform.engine)
    "spectreTestRuntimeOnly"(libs.junit.platform.launcher)
}

val jdkLevel = project.property("jdk.level") as String

tasks {
    named<Test>("test") { useJUnitPlatform() }

    register<Test>("spectreTest") {
        group = "verification"
        description = "Runs opt-in headful Compose Desktop UI tests with Spectre."
        testClassesDirs = spectreTest.output.classesDirs
        classpath = spectreTest.runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 1
        javaLauncher = project.javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jdkLevel) }
        systemProperty("apple.awt.UIElement", "true")
        systemProperty("java.awt.headless", "false")
        systemProperty("jewel.customPopupRender", "true")
    }
}
