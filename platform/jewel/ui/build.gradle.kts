import org.jetbrains.jewel.buildlogic.icons.IconKeysGeneratorTask
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    api(projects.foundation)
    api(project(":jb-icons-api"))
    api(project(":jb-icons-api-rendering"))
    api(project(":jb-icons-api-rendering-lowlevel"))
    api(project(":jb-icons-impl"))
    implementation(compose.components.resources)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}

sourceSets.main { java.srcDir(project.file("generated")) }

tasks {
    withType<LintTask> {
        include("src/**") // Excluding build/ doesn't work for some reason
    }

    withType<FormatTask> {
        include("src/**") // Excluding build/ doesn't work for some reason
    }

    val iconGeneratorTasks = withType<IconKeysGeneratorTask>()
    withType<GenerateMetalavaApiTask> { dependsOn(iconGeneratorTasks) }
}
