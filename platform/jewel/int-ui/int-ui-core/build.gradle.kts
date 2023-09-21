@file:Suppress("UnstableApiUsage")

plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    `intellij-theme-generator`
}

dependencies {
    api(projects.core)
}

intelliJThemeGenerator {
    register("intUiLight") {
        themeClassName = "org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme"
        themeFile = "platform/platform-resources/src/themes/expUI/expUI_light.theme.json"
        ideaVersion = "232.6734"
    }
    register("intUiDark") {
        themeClassName = "org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme"
        themeFile = "platform/platform-resources/src/themes/expUI/expUI_dark.theme.json"
        ideaVersion = "232.6734"
    }
}

tasks {
    named("dokkaHtml") {
        dependsOn("generateIntUiDarkTheme")
        dependsOn("generateIntUiLightTheme")
    }
    named<Jar>("sourcesJar") {
        dependsOn("generateIntUiDarkTheme")
        dependsOn("generateIntUiLightTheme")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
