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
    create("light") {
        paletteClassName("org.jetbrains.jewel.themes.intui.standalone.light.LightPalette")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_light.theme.json")
        ideaVersion("232.6734")
    }
    create("dark") {
        paletteClassName("org.jetbrains.jewel.themes.intui.standalone.dark.DarkPalette")
        themeFile("platform/platform-resources/src/themes/expUI/expUI_dark.theme.json")
        ideaVersion("232.6734")
    }
}
