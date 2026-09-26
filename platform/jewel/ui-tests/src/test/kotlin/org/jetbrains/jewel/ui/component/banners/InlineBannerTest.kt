package org.jetbrains.jewel.ui.component.banners

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.banner.BannerIconActionScope
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InlineBannerTest : SharedBannerTest() {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `when the incoming width is unbounded, wrap the content`() {
        rule.setContent {
            IntUiTheme {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    InlineInformationBanner(
                        text = "Lipsum.",
                        linkActions = { action("Action") {} },
                        iconActions = {
                            iconAction(AllIconsKeys.General.Gear, "Settings") {}
                            iconAction(AllIconsKeys.Actions.Close, "Close") {}
                        },
                    )
                }
            }
        }

        rule.onNodeWithText("Lipsum.").assertIsDisplayed()
        rule.onNodeWithText("Action").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").assertIsDisplayed()

        val bounds = rule.onNodeWithTag("InlineBanner").getBoundsInRoot()
        assertTrue("The banner collapsed to a zero width", bounds.right - bounds.left > 0.dp)
    }

    @Test
    fun `when the banner is taller than its content, keep the link actions inside it`() {
        rule.setContent {
            IntUiTheme {
                InlineInformationBanner(
                    text = "Lipsum.",
                    linkActions = { action("Action") {} },
                    modifier = Modifier.width(720.dp).height(120.dp),
                )
            }
        }

        val bannerBounds = rule.onNodeWithTag("InlineBanner").getBoundsInRoot()
        val textBounds = rule.onNodeWithText("Lipsum.").getBoundsInRoot()
        val linkBounds = rule.onNodeWithText("Action").getBoundsInRoot()

        assertTrue(
            "The link actions were pushed outside the banner (${linkBounds.bottom} > ${bannerBounds.bottom})",
            linkBounds.bottom <= bannerBounds.bottom,
        )
        assertTrue(
            "The link actions were not placed right below the text (gap of ${linkBounds.top - textBounds.bottom})",
            linkBounds.top - textBounds.bottom < 16.dp,
        )
    }

    @Test
    fun `when there are action icons, the title and content must not extend under them`() {
        rule.setContent {
            IntUiTheme {
                InlineInformationBanner(
                    title = "A title",
                    iconActions = {
                        iconAction(AllIconsKeys.General.Gear, "Settings") {}
                        iconAction(AllIconsKeys.Actions.Close, "Close") {}
                    },
                    modifier = Modifier.width(720.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("bannerTextArea"))
                }
            }
        }

        val textArea = rule.onNodeWithTag("bannerTextArea").getBoundsInRoot()
        val leftmostIcon = rule.onNodeWithContentDescription("Settings").getBoundsInRoot()

        assertTrue(
            "The text area runs under the action icons " +
                "(it ends at ${textArea.right}, the icons start at ${leftmostIcon.left})",
            textArea.right <= leftmostIcon.left,
        )
    }

    override fun runBannerTest(
        text: String,
        linkActions: (BannerLinkActionScope.() -> Unit)?,
        iconActions: (BannerIconActionScope.() -> Unit)?,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            IntUiTheme {
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
