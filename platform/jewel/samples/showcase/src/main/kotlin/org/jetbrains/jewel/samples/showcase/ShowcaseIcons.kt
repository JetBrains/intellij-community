// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase

import org.jetbrains.jewel.ui.icon.PathIconKey

/** Icon keys used throughout the Jewel showcase application. */
public object ShowcaseIcons {
    /** Icon for the components menu navigation item. */
    public val componentsMenu: PathIconKey = PathIconKey("icons/structure.svg", ShowcaseIcons::class.java)

    /** The Jewel logo icon. */
    public val jewelLogo: PathIconKey = PathIconKey("icons/jewel-logo.svg", ShowcaseIcons::class.java)

    /** The GitHub icon. */
    public val gitHub: PathIconKey = PathIconKey("icons/github.svg", ShowcaseIcons::class.java)

    /** The Markdown icon. */
    public val markdown: PathIconKey = PathIconKey("icons/markdown.svg", ShowcaseIcons::class.java)

    /** Icon representing the dark theme. */
    public val themeDark: PathIconKey = PathIconKey("icons/darkTheme.svg", ShowcaseIcons::class.java)

    /** Icon representing the light theme. */
    public val themeLight: PathIconKey = PathIconKey("icons/lightTheme.svg", ShowcaseIcons::class.java)

    /** Icon representing the light theme with a light header. */
    public val themeLightWithLightHeader: PathIconKey =
        PathIconKey("icons/lightWithLightHeaderTheme.svg", ShowcaseIcons::class.java)

    /** Icon representing the system (auto) theme. */
    public val themeSystem: PathIconKey = PathIconKey("icons/systemTheme.svg", ShowcaseIcons::class.java)

    /** Icon for the welcome screen. */
    public val welcome: PathIconKey = PathIconKey("icons/meetNewUi.svg", ShowcaseIcons::class.java)

    /** The sunny icon. */
    public val sunny: PathIconKey = PathIconKey("icons/sunny.svg", ShowcaseIcons::class.java)
    /** Icon representing a terminal/console. */
    public val terminal: PathIconKey = PathIconKey("icons/terminal.svg", ShowcaseIcons::class.java)

    /** Icon keys for individual UI component sections in the showcase. */
    public object Components {
        /** Icon for the Badge component section. */
        public val badge: PathIconKey = PathIconKey("icons/components/badge.svg", ShowcaseIcons::class.java)

        /** Icon for the Banners component section. */
        public val banners: PathIconKey = PathIconKey("icons/components/banners.svg", ShowcaseIcons::class.java)

        /** Icon for the Borders component section. */
        public val borders: PathIconKey = PathIconKey("icons/components/borders.svg", ShowcaseIcons::class.java)

        /** Icon for the Brush/paint component section. */
        public val brush: PathIconKey = PathIconKey("icons/components/brush.svg", ShowcaseIcons::class.java)

        /** Icon for the Button component section. */
        public val button: PathIconKey = PathIconKey("icons/components/button.svg", ShowcaseIcons::class.java)

        /** Icon for the Checkbox component section. */
        public val checkbox: PathIconKey = PathIconKey("icons/components/checkBox.svg", ShowcaseIcons::class.java)

        /** Icon for the ComboBox component section. */
        public val comboBox: PathIconKey = PathIconKey("icons/components/comboBox.svg", ShowcaseIcons::class.java)

        /** Icon for the Links component section. */
        public val links: PathIconKey = PathIconKey("icons/components/links.svg", ShowcaseIcons::class.java)

        /** Icon for the Menu component section. */
        public val menu: PathIconKey = PathIconKey("icons/components/menu.svg", ShowcaseIcons::class.java)

        /** Icon for the ProgressBar component section. */
        public val progressBar: PathIconKey = PathIconKey("icons/components/progressbar.svg", ShowcaseIcons::class.java)

        /** Icon for the RadioButton component section. */
        public val radioButton: PathIconKey = PathIconKey("icons/components/radioButton.svg", ShowcaseIcons::class.java)

        /** Icon for the Scrollbar component section. */
        public val scrollbar: PathIconKey = PathIconKey("icons/components/scrollbar.svg", ShowcaseIcons::class.java)

        /** Icon for the SegmentedControls component section. */
        public val segmentedControls: PathIconKey =
            PathIconKey("icons/components/segmentedControl.svg", ShowcaseIcons::class.java)

        /** Icon for the Slider component section. */
        public val slider: PathIconKey = PathIconKey("icons/components/slider.svg", ShowcaseIcons::class.java)

        /** Icon for the SplitLayout component section. */
        public val splitlayout: PathIconKey = PathIconKey("icons/components/splitLayout.svg", ShowcaseIcons::class.java)

        /** Icon for the Tabs component section. */
        public val tabs: PathIconKey = PathIconKey("icons/components/tabs.svg", ShowcaseIcons::class.java)

        /** Icon for the TextArea component section. */
        public val textArea: PathIconKey = PathIconKey("icons/components/textArea.svg", ShowcaseIcons::class.java)

        /** Icon for the TextField component section. */
        public val textField: PathIconKey = PathIconKey("icons/components/textField.svg", ShowcaseIcons::class.java)

        /** Icon for the Toolbar component section. */
        public val toolbar: PathIconKey = PathIconKey("icons/components/toolbar.svg", ShowcaseIcons::class.java)

        /** Icon for the Tooltip component section. */
        public val tooltip: PathIconKey = PathIconKey("icons/components/tooltip.svg", ShowcaseIcons::class.java)

        /** Icon for the Tree component section. */
        public val tree: PathIconKey = PathIconKey("icons/components/tree.svg", ShowcaseIcons::class.java)

        /** Icon for the Typography component section. */
        public val typography: PathIconKey = PathIconKey("icons/components/typography.svg", ShowcaseIcons::class.java)

        /** Icon for the SpeedSearch component section. */
        public val speedSearch: PathIconKey = PathIconKey("icons/components/speedSearch.svg", ShowcaseIcons::class.java)
    }

    /** Icon keys for programming language representations used in the showcase. */
    public object ProgrammingLanguages {
        /** Icon representing the Kotlin programming language. */
        public val Kotlin: PathIconKey = PathIconKey("icons/kotlin.svg", ShowcaseIcons::class.java)
    }
}
