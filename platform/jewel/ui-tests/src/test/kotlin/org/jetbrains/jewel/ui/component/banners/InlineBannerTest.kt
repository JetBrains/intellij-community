package org.jetbrains.jewel.ui.component.banners

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntUiTestTheme
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.banner.BannerIconActionScope
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.junit.Rule

class InlineBannerTest : SharedBannerTest() {
    @get:Rule val rule = createComposeRule()

    override fun runBannerTest(
        text: String,
        linkActions: (BannerLinkActionScope.() -> Unit)?,
        iconActions: (BannerIconActionScope.() -> Unit)?,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            IntUiTestTheme {
                InlineInformationBanner(
                    text = text,
                    linkActions = linkActions,
                    iconActions = iconActions,
                    modifier = Modifier.width(720.dp),
                )
            }
        }
        rule.block()
    }
}
