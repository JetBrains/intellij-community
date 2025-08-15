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
    }
    register("intUiDark") {
        themeClassName = "org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme"
        themeFilePath = "../../platform/platform-resources/src/themes/expUI/expUI_dark.theme.json"
    }
}
