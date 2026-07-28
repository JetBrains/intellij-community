import org.jetbrains.jewel.buildlogic.icons.IconKeysGeneratorTask
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask
import org.jetbrains.jewel.buildlogic.theme.DefaultColorPaletteGeneratorTask
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    jewel
    `jewel-check-public-api`
    `intellij-color-palette-generator`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    api(projects.foundation)
    api(project(":jb-icons-api"))
    api(project(":jb-icons-api-rendering"))
    api(project(":jb-icons-impl"))
    implementation(compose.components.resources)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}

sourceSets.main { java.srcDir(project.file("generated/icons")) }

tasks {
    withType<LintTask> {
        include("src/**") // Excluding build/ doesn't work for some reason
    }

    withType<FormatTask> {
        include("src/**") // Excluding build/ doesn't work for some reason
    }

    val iconGeneratorTasks = withType<IconKeysGeneratorTask>()
    withType<GenerateMetalavaApiTask> { dependsOn(iconGeneratorTasks) }

    // Same pattern as int-ui-standalone/build.gradle.kts for its own generated theme files.
    val paletteGeneratorTasks = withType<DefaultColorPaletteGeneratorTask>()
    paletteGeneratorTasks.configureEach { finalizedBy(ktfmtFormatMain) }

    ktfmtFormatMain {
        // Ensure the ktfmtFormatMain task is not considered UP-TO-DATE when we've regenerated
        // DefaultColorPalette.kt (it doesn't always pick it up for some reason)
        outputs.upToDateWhen { paletteGeneratorTasks.none { it.state.executed } }
    }

    ktfmtCheckMain {
        mustRunAfter(paletteGeneratorTasks)
        mustRunAfter(ktfmtFormatMain)
    }
}

intelliJColorPaletteGenerator {
    register("Light") {
        themeFilePath = "../../platform/platform-resources/src/themes/expUI/expUI_light.theme.json"
        propertyName = "Light"
        isIslands = false
    }

    register("Dark") {
        themeFilePath = "../../platform/platform-resources/src/themes/expUI/expUI_dark.theme.json"
        propertyName = "Dark"
        isIslands = false
    }

    register("IslandsLight") {
        themeFilePath = "../../platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json"
        propertyName = "IslandsLight"
        isIslands = true
    }

    register("IslandsDark") {
        themeFilePath = "../../platform/platform-resources/src/themes/islands/ManyIslandsDark.theme.json"
        propertyName = "IslandsDark"
        isIslands = true
    }
}
