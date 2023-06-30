plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    `intui-palette-generator`
}

dependencies {
    api(projects.core)
    api(projects.composeUtils)
    api(projects.foundation)
}

intUiPaletteGenerator {
    val ideaVersion = "232.6734"

    create("intUiLight") {
        paletteClassName("org.jetbrains.jewel.themes.intui.core.palette.IntUiLightPalette")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_light.theme.json")
        ideaVersion(ideaVersion)
    }
    create("intUiDark") {
        paletteClassName("org.jetbrains.jewel.themes.intui.core.palette.IntUiDarkPalette")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_dark.theme.json")
        ideaVersion(ideaVersion)
    }
}
