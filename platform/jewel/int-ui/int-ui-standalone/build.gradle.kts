import dev.detekt.gradle.Detekt
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask
import org.jetbrains.jewel.buildlogic.theme.IntelliJThemeGeneratorTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    `intellij-theme-generator`
}

dependencies {
    api(projects.ui)
    implementation(libs.jbr.api)
}

intelliJThemeGenerator {
    register("intUiLight") {
        themeClassName = "org.jetbrains.jewel.intui.core.theme.IntUiLightTheme"
        themeFilePath = "../../platform/platform-resources/src/themes/expUI/expUI_light.theme.json"
        targetDir = project.file("generated/theme/")
    }

    register("intUiDark") {
        themeClassName = "org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme"
        themeFilePath = "../../platform/platform-resources/src/themes/expUI/expUI_dark.theme.json"
        targetDir = project.file("generated/theme/")
    }
}

tasks {
    val themeGeneratorTasks = withType<IntelliJThemeGeneratorTask>()
    themeGeneratorTasks.configureEach { finalizedBy(ktfmtFormatMain) }

    ktfmtFormatMain {
        // Ensure the ktfmtFormatMain task is not considered UP-TO-DATE when
        // we've regenerated the theme definitions (it doesn't always pick
        // it up for some reason)
        outputs.upToDateWhen { themeGeneratorTasks.none { it.state.executed } }
    }

    val generateThemes by
        register<Task>("generateThemes") {
            description = "Updates the ThemeDescription dumps and reformats them."
            dependsOn(themeGeneratorTasks)
            dependsOn(ktfmtFormatMain)
        }

    withType<GenerateMetalavaApiTask> { dependsOn(generateThemes) }

    ktfmtCheckMain {
        mustRunAfter(generateThemes)
        mustRunAfter(ktfmtFormatMain)
    }

    withType<LintTask> {
        mustRunAfter(generateThemes)
        mustRunAfter(ktfmtFormatMain)
    }

    withType<Detekt>().configureEach {
        mustRunAfter(generateThemes)
        mustRunAfter(ktfmtFormatMain)
    }

    detektMain {
        mustRunAfter(generateThemes)
        mustRunAfter(ktfmtFormatMain)
    }
}
