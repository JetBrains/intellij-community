plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    `intellij-theme-generator`
}

dependencies {
    api(projects.core)
    api(projects.composeUtils)
    api(projects.foundation)
}

intelliJThemeGenerator {
    val ideaVersion = "232.6734"

    create("intUiLight") {
        themeClassName("org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_light.theme.json")
        ideaVersion(ideaVersion)
    }
    create("intUiDark") {
        themeClassName("org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_dark.theme.json")
        ideaVersion(ideaVersion)
    }
}
