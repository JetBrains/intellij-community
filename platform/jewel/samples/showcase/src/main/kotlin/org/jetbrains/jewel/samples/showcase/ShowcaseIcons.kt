// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase

import org.jetbrains.jewel.ui.icon.PathIconKey

public object ShowcaseIcons {
    public val componentsMenu: PathIconKey = PathIconKey("icons/structure.svg", ShowcaseIcons::class.java)
    public val gitHub: PathIconKey = PathIconKey("icons/github.svg", ShowcaseIcons::class.java)
    public val jewelLogo: PathIconKey = PathIconKey("icons/jewel-logo.svg", ShowcaseIcons::class.java)
    public val markdown: PathIconKey = PathIconKey("icons/markdown.svg", ShowcaseIcons::class.java)
    public val themeDark: PathIconKey = PathIconKey("icons/darkTheme.svg", ShowcaseIcons::class.java)
    public val themeLight: PathIconKey = PathIconKey("icons/lightTheme.svg", ShowcaseIcons::class.java)
    public val themeLightWithLightHeader: PathIconKey =
        PathIconKey("icons/lightWithLightHeaderTheme.svg", ShowcaseIcons::class.java)
    public val themeSystem: PathIconKey = PathIconKey("icons/systemTheme.svg", ShowcaseIcons::class.java)
    public val welcome: PathIconKey = PathIconKey("icons/meetNewUi.svg", ShowcaseIcons::class.java)

    public object Components {
        public val banners: PathIconKey = PathIconKey("icons/components/banners.svg", ShowcaseIcons::class.java)
        public val borders: PathIconKey = PathIconKey("icons/components/borders.svg", ShowcaseIcons::class.java)
        public val brush: PathIconKey = PathIconKey("icons/components/brush.svg", ShowcaseIcons::class.java)
        public val button: PathIconKey = PathIconKey("icons/components/button.svg", ShowcaseIcons::class.java)
        public val checkbox: PathIconKey = PathIconKey("icons/components/checkBox.svg", ShowcaseIcons::class.java)
        public val comboBox: PathIconKey = PathIconKey("icons/components/comboBox.svg", ShowcaseIcons::class.java)
        public val links: PathIconKey = PathIconKey("icons/components/links.svg", ShowcaseIcons::class.java)
        public val progressBar: PathIconKey = PathIconKey("icons/components/progressbar.svg", ShowcaseIcons::class.java)
        public val radioButton: PathIconKey = PathIconKey("icons/components/radioButton.svg", ShowcaseIcons::class.java)
        public val scrollbar: PathIconKey = PathIconKey("icons/components/scrollbar.svg", ShowcaseIcons::class.java)
        public val segmentedControls: PathIconKey =
            PathIconKey("icons/components/segmentedControl.svg", ShowcaseIcons::class.java)
        public val slider: PathIconKey = PathIconKey("icons/components/slider.svg", ShowcaseIcons::class.java)
        public val splitlayout: PathIconKey = PathIconKey("icons/components/splitLayout.svg", ShowcaseIcons::class.java)
        public val tabs: PathIconKey = PathIconKey("icons/components/tabs.svg", ShowcaseIcons::class.java)
        public val textArea: PathIconKey = PathIconKey("icons/components/textArea.svg", ShowcaseIcons::class.java)
        public val textField: PathIconKey = PathIconKey("icons/components/textField.svg", ShowcaseIcons::class.java)
        public val toolbar: PathIconKey = PathIconKey("icons/components/toolbar.svg", ShowcaseIcons::class.java)
        public val tooltip: PathIconKey = PathIconKey("icons/components/tooltip.svg", ShowcaseIcons::class.java)
        public val tree: PathIconKey = PathIconKey("icons/components/tree.svg", ShowcaseIcons::class.java)
        public val typography: PathIconKey = PathIconKey("icons/components/typography.svg", ShowcaseIcons::class.java)
    }

    public object ProgrammingLanguages {
        public val Kotlin: PathIconKey = PathIconKey("icons/kotlin.svg", ShowcaseIcons::class.java)
    }
}
