// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlinx.coroutines.delay
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.gotit.GotItButtons.Companion.hasNoButtons
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider
import org.jetbrains.jewel.ui.popupShadowAndBorder
import org.jetbrains.jewel.ui.util.isDark

private val DEFAULT_TOOLTIP_WIDTH = 280.dp
private val DEFAULT_TOOLTIP_EXTENDED_WIDTH = 328.dp

/**
 * A Got It tooltip component that provides contextual information about new or changed features.
 *
 * Got It tooltips are specialized popups (called "Balloons" in the Swing implementation) that:
 * - Appear near UI elements to guide users
 * - Can display icons or step numbers
 * - Support rich content (headers, links, images, buttons)
 * - Auto-hide after a timeout (optional, mutually exclusive with buttons)
 * - Display an arrow pointing to the target element
 * - Can limit how many times they appear (showCount)
 *
 * **Important:** When a timeout is set, buttons are automatically hidden. The tooltip will auto-dismiss after the
 * timeout duration. This matches the Swing implementation behavior.
 *
 * **Dismissal:** This component never removes itself from the UI. When a button is clicked, or when the timeout
 * elapses, [onDismiss] is called — but the tooltip stays visible until the caller reacts by setting [visible] to
 * `false`. Always wire [onDismiss] to update your state, otherwise the tooltip cannot be closed.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/got-it-tooltip.html)
 *
 * **Swing equivalent:**
 * [`GotItTooltip`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/GotItTooltip.kt)
 *
 * **Usage example:**
 *
 * ```kotlin
 * var showTooltip by remember { mutableStateOf(false) }
 *
 * GotItTooltip(
 *     text = "This is a new feature!",
 *     visible = showTooltip,
 *     onDismiss = { showTooltip = false },
 * ) {
 *     Button(onClick = { showTooltip = true }) {
 *         Text("Click me")
 *     }
 * }
 * ```
 *
 * @param text The main content text of the component
 * @param visible Controls the visibility of the component.
 * @param onDismiss Called when a button is clicked or the timeout elapses. Must set [visible] to `false` to actually
 *   hide the tooltip; without this the popup remains on screen indefinitely.
 * @param modifier Modifier to apply to the content wrapper
 * @param header Optional header text displayed above the main content
 * @param iconOrStep Optional icon or step number to display (mutually exclusive)
 * @param buttons Button configuration (primary and optional secondary button). Each button's [GotItButton.action] runs
 *   as a side effect before [onDismiss] is called automatically.
 * @param link Type of link to display below the text
 * @param image Optional image to display at the top of the tooltip
 * @param maxWidth Optional fixed text width. When set, auto-extension (280→328 dp) is disabled. **Ignored when an image
 *   is present** — the image width always takes priority. Matches `GotItComponentBuilder.withMaxWidth()`.
 * @param timeout Auto-hide timeout. Buttons are hidden and [onDismiss] is called after this duration elapses. Use
 *   [Duration.INFINITE] (the default) to disable the timeout.
 * @param padding Extra gap between the balloon arrow tip and the anchor point. Pushes the balloon away from the
 *   component it is anchored to.
 * @param onShow Callback invoked when the tooltip is displayed
 * @param onEscapePress Code to run when users press escape while the popup is focused. If set, it'll automatically call
 *   [onDismiss]
 * @param style Visual styling configuration
 * @param content The target component to which this tooltip is anchored
 */
@Composable
public fun GotItTooltip(
    @Nls text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @Nls header: String? = null,
    iconOrStep: GotItIconOrStep? = null,
    buttons: GotItButtons = GotItButtons.Default,
    link: GotItLink? = null,
    image: GotItImage? = null,
    maxWidth: Dp? = null,
    timeout: Duration = Duration.INFINITE,
    gotItBalloonPosition: GotItBalloonPosition = GotItBalloonPosition.BELOW,
    anchor: Alignment = Alignment.BottomCenter,
    padding: Dp = 0.dp,
    onShow: () -> Unit = {},
    onEscapePress: (() -> Unit)? = null,
    style: GotItTooltipStyle = LocalGotItTooltipStyle.current,
    content: @Composable () -> Unit,
) {
    GotItTooltip(
        body = buildGotItBody { append(text) },
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        header = header,
        iconOrStep = iconOrStep,
        buttons = buttons,
        link = link,
        image = image,
        maxWidth = maxWidth,
        timeout = timeout,
        gotItBalloonPosition = gotItBalloonPosition,
        anchor = anchor,
        offset = padding,
        onShow = onShow,
        onEscapePress = onEscapePress,
        style = style,
        content = content,
    )
}

/**
 * A Got It tooltip component that provides contextual information about new or changed features, with support for rich
 * body content (bold, code, inline links, browser links).
 *
 * Build the body with [buildGotItBody]:
 * ```kotlin
 * GotItTooltip(
 *     body = buildGotItBody {
 *         append("Press ")
 *         bold("Resume")
 *         append(", or ")
 *         link("open the docs") { openUrl("https://example.com") }
 *         append(".")
 *     },
 *     visible = showTooltip,
 *     onDismiss = { showTooltip = false },
 * ) { ... }
 * ```
 *
 * @param body The rich-text body built with [buildGotItBody]
 * @param visible Controls the visibility of the component.
 * @param onDismiss Called when a button is clicked or the timeout elapses. Must set [visible] to `false` to actually
 *   hide the tooltip; without this the popup remains on screen indefinitely.
 * @param modifier Modifier to apply to the content wrapper
 * @param header Optional header text displayed above the main content
 * @param iconOrStep Optional icon or step number to display (mutually exclusive)
 * @param buttons Button configuration (primary and optional secondary button). Each button's [GotItButton.action] runs
 *   as a side effect before [onDismiss] is called automatically.
 * @param link Type of link to display below the text
 * @param image Optional image to display at the top of the tooltip
 * @param maxWidth Optional fixed text width. When set, auto-extension (280→328 dp) is disabled. **Ignored when an image
 *   is present** — the image width always takes priority. Matches `GotItComponentBuilder.withMaxWidth()`.
 * @param timeout Auto-hide timeout. Buttons are hidden and [onDismiss] is called after this duration elapses. Use
 *   [Duration.INFINITE] (the default) to disable the timeout.
 * @param offset Extra gap between the balloon arrow tip and the anchor point. Pushes the balloon away from the
 *   component it is anchored to.
 * @param onShow Callback invoked when the tooltip is displayed
 * @param onEscapePress Code to run when users press escape while the popup is focused.
 * @param style Visual styling configuration
 * @param content The target component to which this tooltip is anchored
 */
@OptIn(InternalJewelApi::class)
@Composable
public fun GotItTooltip(
    body: GotItBody,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @Nls header: String? = null,
    iconOrStep: GotItIconOrStep? = null,
    buttons: GotItButtons = GotItButtons.Default,
    link: GotItLink? = null,
    image: GotItImage? = null,
    maxWidth: Dp? = null,
    timeout: Duration = Duration.INFINITE,
    gotItBalloonPosition: GotItBalloonPosition = GotItBalloonPosition.BELOW,
    anchor: Alignment = Alignment.BottomCenter,
    offset: Dp = 0.dp,
    onShow: () -> Unit = {},
    onEscapePress: (() -> Unit)? = null,
    style: GotItTooltipStyle = LocalGotItTooltipStyle.current,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val cornerRadiusPx = style.metrics.cornerRadius.value.roundToInt()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnShow by rememberUpdatedState(onShow)
    val currentOnEscapePress by rememberUpdatedState(onEscapePress)
    val shouldListenForEscapeKeyPress by
        rememberUpdatedState(visible && currentOnEscapePress != null || buttons.hasNoButtons())

    Box(
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                if (shouldListenForEscapeKeyPress && event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    currentOnEscapePress?.invoke()
                    currentOnDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        content()

        if (visible) {
            Popup(
                popupPositionProvider =
                    rememberGotItTooltipBalloonPopupPositionProvider(gotItBalloonPosition, anchor, padding = offset),
                cornerSize = CornerSize(style.metrics.cornerRadius),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
                onDismissRequest = {},
                windowShape = { logicalSize ->
                    createBalloonAwtShape(
                        size = logicalSize,
                        arrowWidthPx = 16,
                        arrowHeightPx = 8,
                        cornerRadiusPx = cornerRadiusPx,
                        arrowPosition = gotItBalloonPosition,
                        arrowOffsetPx = 24,
                        layoutDirection = layoutDirection,
                    )
                },
            ) {
                GotItTooltipBalloonContainer(modifier = modifier, gotItBalloonPosition = gotItBalloonPosition) {
                    GotItTooltipImpl(
                        body = body,
                        header = header,
                        iconOrStep = iconOrStep,
                        // Buttons are not shown when a finite timeout is set
                        buttons = if (timeout.isFinite()) GotItButtons.None else buttons,
                        link = link,
                        image = image,
                        maxWidth = maxWidth,
                        onDismiss = onDismiss,
                        style = style,
                    )

                    LaunchedEffect(Unit) { currentOnShow() }

                    if (timeout.isFinite()) {
                        LaunchedEffect(Unit) {
                            delay(timeout)
                            currentOnDismiss()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Represents either an icon or a step number for the Got It tooltip. Icon and step number are mutually exclusive - only
 * one can be shown at a time.
 */
@Stable
public sealed interface GotItIconOrStep {
    /**
     * Display a custom icon.
     *
     * @param content Composable icon content to display
     */
    @Stable
    @GenerateDataFunctions
    public class Icon(public val content: @Composable () -> Unit) : GotItIconOrStep {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Icon

            return content == other.content
        }

        override fun hashCode(): Int = content.hashCode()

        override fun toString(): String = "Icon(content=$content)"
    }

    /**
     * Display a step number (e.g., "01", "02", "10").
     *
     * @param number Step number in the range [1, 99]
     * @throws IllegalArgumentException if number is not in [1, 99]
     */
    @Stable
    @GenerateDataFunctions
    public class Step(public val number: Int) : GotItIconOrStep {
        init {
            require(number in 1..99) { "Step number must be in range [1, 99], got: $number" }
        }

        /** Formatted step text with leading zero for numbers < 10 (e.g., "01", "02", "10") */
        public val formattedText: String = number.toString().padStart(2, '0')

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Step

            if (number != other.number) return false
            if (formattedText != other.formattedText) return false

            return true
        }

        override fun hashCode(): Int {
            var result = number
            result = 31 * result + formattedText.hashCode()
            return result
        }

        override fun toString(): String = "Step(number=$number, formattedText='$formattedText')"
    }
}

/**
 * Configuration for buttons displayed in the Got It tooltip.
 *
 * @param primary Primary button ("Got it" by default). Null means no buttons are shown.
 * @param secondary Optional secondary button for additional actions
 */
@Stable
@GenerateDataFunctions
public class GotItButtons(public val primary: GotItButton?, public val secondary: GotItButton? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItButtons

        if (primary != other.primary) return false
        if (secondary != other.secondary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primary?.hashCode() ?: 0
        result = 31 * result + (secondary?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "GotItButtons(primary=$primary, secondary=$secondary)"

    public companion object {
        /** Default configuration with a "Got it" primary button */
        public val Default: GotItButtons = GotItButtons(primary = GotItButton.Default)

        /** Configuration with no buttons */
        public val None: GotItButtons = GotItButtons(primary = null)

        internal fun GotItButtons.hasNoButtons() = this.primary == null && this.secondary == null
    }
}

/**
 * Configuration for a single button in the Got It tooltip.
 *
 * @param label Button label text
 * @param action Optional side effect to run when the button is clicked, before the tooltip is dismissed. The tooltip is
 *   always dismissed automatically after this runs — there is no need to call [onDismiss][GotItTooltip] here.
 */
@Stable
@GenerateDataFunctions
public class GotItButton(@Nls public val label: String, public val action: () -> Unit = {}) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItButton

        if (label != other.label) return false
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + action.hashCode()
        return result
    }

    override fun toString(): String = "GotItButton(label='$label', action=$action)"

    public companion object {
        /** Default "Got it" button with no extra action */
        public val Default: GotItButton = GotItButton(label = "Got it")
    }
}

/**
 * Represents a link in the Got It tooltip. Links are displayed below the main text as separate interactive elements.
 */
@Stable
public sealed interface GotItLink {
    public val label: String
    public val action: () -> Unit

    /**
     * A regular internal link (blue text with underline).
     *
     * @param label Link text to display
     * @param action Action to perform when clicked
     */
    @Stable
    @GenerateDataFunctions
    public class Regular(@Nls override val label: String, override val action: () -> Unit) : GotItLink {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Regular

            if (label != other.label) return false
            if (action != other.action) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + action.hashCode()
            return result
        }

        override fun toString(): String = "Regular(label='$label', action=$action)"
    }

    /**
     * A browser link that opens an external URL (displayed with an arrow icon).
     *
     * @param label Link text to display
     * @param uri URI to open in the browser
     * @param action Optional side effect callback invoked when the link is clicked (e.g., for analytics). The URI is
     *   always opened in the browser regardless of this callback.
     */
    @Stable
    @GenerateDataFunctions
    public class Browser(
        @Nls override val label: String,
        public val uri: String,
        override val action: () -> Unit = {},
    ) : GotItLink {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Browser

            if (label != other.label) return false
            if (uri != other.uri) return false
            if (action != other.action) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + uri.hashCode()
            result = 31 * result + action.hashCode()
            return result
        }

        override fun toString(): String = "Browser(label='$label', uri='$uri', action=$action)"
    }
}

/**
 * Configuration for an image displayed in the Got It tooltip.
 *
 * The image is loaded from the classpath using the thread's context classloader, which in IntelliJ Platform resolves to
 * the calling plugin's classloader automatically.
 *
 * @param path Classpath-relative path to the image resource (e.g. `"images/promo.png"`)
 * @param contentDescription The content description for the image
 * @param showBorder Whether to show a border around the image
 */
@Stable
@GenerateDataFunctions
public class GotItImage(
    public val path: String,
    public val contentDescription: String?,
    public val showBorder: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItImage

        if (showBorder != other.showBorder) return false
        if (path != other.path) return false
        if (contentDescription != other.contentDescription) return false

        return true
    }

    override fun hashCode(): Int {
        var result = showBorder.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + (contentDescription?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "GotItImage(" + "path='$path', " + "contentDescription=$contentDescription, " + "showBorder=$showBorder" + ")"
}

/**
 * Position of the Got It tooltip balloon relative to the target component. The arrow is drawn on the opposite side of
 * the position (e.g., BELOW means tooltip below target with arrow pointing up).
 *
 * The arrow tip will always point to the center of the provided anchor.
 */
public enum class GotItBalloonPosition {
    /** Tooltip appears below the target, arrow points up */
    BELOW,

    /** Tooltip appears above the target, arrow points down */
    ABOVE,

    /** Tooltip appears to the start of the target, arrow points end */
    START,

    /** Tooltip appears to the end of the target, arrow points start */
    END,
}

@Composable
private fun GotItTooltipImpl(
    body: GotItBody,
    header: String?,
    iconOrStep: GotItIconOrStep?,
    buttons: GotItButtons,
    link: GotItLink?,
    image: GotItImage?,
    maxWidth: Dp?,
    onDismiss: () -> Unit,
    style: GotItTooltipStyle,
) {
    CompositionLocalProvider(
        LocalContentColor provides style.colors.foreground,
        LocalTextStyle provides LocalTextStyle.current.copy(color = style.colors.foreground),
    ) {
        Box {
            OverrideDarkMode(isDark = style.colors.background.isDark()) {
                GotItTooltipContent(body, header, iconOrStep, buttons, link, image, maxWidth, onDismiss, style)
            }
        }
    }
}

@Composable
private fun GotItTooltipContent(
    body: GotItBody,
    header: String?,
    iconOrStep: GotItIconOrStep?,
    buttons: GotItButtons,
    link: GotItLink?,
    image: GotItImage?,
    maxWidth: Dp?,
    onDismiss: () -> Unit,
    style: GotItTooltipStyle,
) {
    var imageWidth by remember { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    val editorTextStyle = JewelTheme.editorTextStyle
    val externalLinkIconKey = LocalLinkStyle.current.icons.externalLink

    // Auto-extension (280 → 328 dp) is only allowed when there is no image and no custom maxWidth
    val allowWidthExtension = image == null && maxWidth == null
    var currentWidth by remember(maxWidth) { mutableStateOf(maxWidth ?: DEFAULT_TOOLTIP_WIDTH) }

    val annotatedBody =
        remember(body, style.colors.link, style.colors.codeForeground, style.colors.codeBackground) {
            buildBodyAnnotatedString(body, style.colors)
        }
    val inlineContent =
        remember(body, style.colors.codeForeground, style.colors.codeBackground, externalLinkIconKey) {
            buildInlineContent(body, style.colors, editorTextStyle, externalLinkIconKey)
        }

    val contentWidthModifier =
        when {
            image != null -> Modifier.width(imageWidth ?: DEFAULT_TOOLTIP_WIDTH)
            maxWidth != null -> Modifier.widthIn(max = maxWidth)
            else -> Modifier.widthIn(max = currentWidth)
        }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        image?.let {
            Box(
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        imageWidth = with(density) { coordinates.size.width.toDp() }
                    }
            ) {
                ImageContent(image = it, style = style)
            }
        }

        Column(modifier = contentWidthModifier) {
            Row(verticalAlignment = Alignment.Top) {
                iconOrStep?.let { iconOrStepValue ->
                    IconOrStepContent(iconOrStep = iconOrStepValue, style = style)
                    Spacer(modifier = Modifier.width(style.metrics.iconPadding))
                }

                Column {
                    if (!header.isNullOrEmpty()) {
                        Text(text = header, fontWeight = FontWeight.Bold, color = style.colors.headerForeground)
                        Spacer(modifier = Modifier.height(style.metrics.textPadding))
                    }

                    Text(
                        text = annotatedBody,
                        color = style.colors.foreground,
                        inlineContent = inlineContent,
                        onTextLayout = { result ->
                            if (allowWidthExtension && result.lineCount >= 5 && currentWidth == DEFAULT_TOOLTIP_WIDTH) {
                                currentWidth = DEFAULT_TOOLTIP_EXTENDED_WIDTH
                            }
                        },
                    )

                    if (link != null) {
                        Spacer(Modifier.height(style.metrics.textPadding))
                        LinkContent(link, style)
                    }

                    if (buttons.primary != null) {
                        ButtonsContent(buttons, onDismiss, style)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageContent(image: GotItImage, style: GotItTooltipStyle) {
    Box(
        modifier =
            Modifier.padding(style.metrics.imagePadding)
                .clip(RoundedCornerShape(style.metrics.cornerRadius))
                .then(
                    if (image.showBorder) {
                        Modifier.border(
                            width = 1.dp,
                            color = style.colors.imageBorderColor,
                            shape = RoundedCornerShape(style.metrics.cornerRadius),
                        )
                    } else {
                        Modifier
                    }
                )
    ) {
        val classLoader = Thread.currentThread().contextClassLoader ?: GotItImage::class.java.classLoader
        val painterProvider = remember(image.path, classLoader) { ResourcePainterProvider(image.path, classLoader) }
        val painter by painterProvider.getPainter()

        // Render at pixel dimensions treated as dp, matching IJP's Swing behavior where iconWidth/iconHeight
        // are used directly as logical pixels (not density-divided). Without this, BitmapPainter would render
        // the image at intrinsicSize/density dp, making it appear 2x smaller on Retina displays.
        val intrinsicSize = painter.intrinsicSize
        val sizeModifier =
            if (intrinsicSize.isSpecified) {
                Modifier.size(intrinsicSize.width.dp, intrinsicSize.height.dp)
            } else {
                Modifier
            }

        Image(painter, image.contentDescription, modifier = sizeModifier)
    }
}

@Composable
private fun IconOrStepContent(iconOrStep: GotItIconOrStep, style: GotItTooltipStyle) {
    when (iconOrStep) {
        is GotItIconOrStep.Icon -> {
            iconOrStep.content()
        }

        is GotItIconOrStep.Step -> {
            Text(
                text = iconOrStep.formattedText,
                color = style.colors.stepForeground,
                style = JewelTheme.editorTextStyle,
            )
        }
    }
}

@Composable
private fun LinkContent(link: GotItLink, style: GotItTooltipStyle) {
    val currentLinkStyle = LocalLinkStyle.current
    val gotItLinkColor = style.colors.link
    val overriddenLinkStyle =
        LinkStyle(
            colors =
                LinkColors(
                    content = gotItLinkColor,
                    contentDisabled = currentLinkStyle.colors.contentDisabled,
                    contentFocused = gotItLinkColor,
                    contentPressed = gotItLinkColor,
                    contentHovered = gotItLinkColor,
                    contentVisited = gotItLinkColor,
                ),
            metrics = currentLinkStyle.metrics,
            icons = currentLinkStyle.icons,
            underlineBehavior = currentLinkStyle.underlineBehavior,
        )

    Column {
        when (link) {
            is GotItLink.Regular -> {
                Link(text = link.label, onClick = link.action, style = overriddenLinkStyle)
            }

            is GotItLink.Browser -> {
                val uriHandler = LocalUriHandler.current
                ExternalLink(
                    text = link.label,
                    onClick = {
                        uriHandler.openUri(link.uri)
                        link.action()
                    },
                    style = overriddenLinkStyle,
                )
            }
        }

        Spacer(modifier = Modifier.height(style.metrics.textPadding))
    }
}

@Composable
private fun ButtonsContent(buttons: GotItButtons, onDismiss: () -> Unit, style: GotItTooltipStyle) {
    val gotItButtonStyle = LocalGotItButtonStyle.current
    val currentLinkStyle = LocalLinkStyle.current
    val gotItSecondaryButtonColor = style.colors.secondaryActionForeground
    val overriddenLinkStyle =
        LinkStyle(
            colors =
                LinkColors(
                    content = gotItSecondaryButtonColor,
                    contentDisabled = currentLinkStyle.colors.contentDisabled,
                    contentFocused = gotItSecondaryButtonColor,
                    contentPressed = gotItSecondaryButtonColor,
                    contentHovered = gotItSecondaryButtonColor,
                    contentVisited = gotItSecondaryButtonColor,
                ),
            metrics = currentLinkStyle.metrics,
            icons = currentLinkStyle.icons,
            underlineBehavior = currentLinkStyle.underlineBehavior,
        )

    Row(modifier = Modifier.padding(style.metrics.buttonPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        buttons.primary?.let { primaryButton ->
            DefaultButton(
                onClick = {
                    primaryButton.action()
                    onDismiss()
                },
                style = gotItButtonStyle,
            ) {
                Text(text = primaryButton.label)
            }
        }

        buttons.secondary?.let { secondaryButton ->
            Link(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = secondaryButton.label,
                onClick = {
                    secondaryButton.action()
                    onDismiss()
                },
                style = overriddenLinkStyle,
            )
        }
    }
}

private class BalloonShape(
    private val arrowWidth: Dp = 16.dp,
    private val arrowHeight: Dp = 16.dp,
    private val cornerRadius: Dp = 8.dp,
    private val arrowPosition: GotItBalloonPosition,
    private val arrowOffset: Dp,
) : Shape {
    @OptIn(InternalJewelApi::class)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        createBalloonOutline(
            size = size,
            layoutDirection = layoutDirection,
            density = density,
            arrowWidth = arrowWidth,
            arrowHeight = arrowHeight,
            cornerRadius = cornerRadius,
            arrowPosition = arrowPosition,
            arrowOffset = arrowOffset,
        )
}

@Composable
private fun GotItTooltipBalloonContainer(
    modifier: Modifier = Modifier,
    gotItBalloonPosition: GotItBalloonPosition = GotItBalloonPosition.ABOVE,
    content: @Composable () -> Unit,
) {
    val style = LocalGotItTooltipStyle.current
    val arrowWidth = 16.dp
    val arrowHeight = 8.dp
    val arrowOffset: Dp = 24.dp

    val balloonShape =
        BalloonShape(arrowWidth, arrowHeight, style.metrics.cornerRadius, gotItBalloonPosition, arrowOffset)

    Box(
        modifier =
            modifier
                .popupShadowAndBorder(
                    shape = balloonShape,
                    shadowSize = 1.dp,
                    shadowColor = style.colors.background,
                    borderWidth = 1.dp,
                    borderColor = style.colors.balloonBorderColor,
                )
                .background(style.colors.background, balloonShape)
                .padding(
                    start = if (gotItBalloonPosition == GotItBalloonPosition.END) arrowHeight else 0.dp,
                    top = if (gotItBalloonPosition == GotItBalloonPosition.BELOW) arrowHeight else 0.dp,
                    end = if (gotItBalloonPosition == GotItBalloonPosition.START) arrowHeight else 0.dp,
                    bottom = if (gotItBalloonPosition == GotItBalloonPosition.ABOVE) arrowHeight else 0.dp,
                )
                .padding(style.metrics.contentPadding)
    ) {
        content()
    }
}

/**
 * Creates and remembers a [PopupPositionProvider] that positions a balloon popup so that its arrow (drawn at
 * [arrowOffset] from the leading edge of the balloon) aligns exactly with the [anchor] point on the target component.
 *
 * Use this together with [GotItTooltipBalloonContainer] inside a [Popup] to get correct hit-testing bounds — no
 * `graphicsLayer` translation is applied, so the layout bounds and the visual bounds always match.
 *
 * @param gotItBalloonPosition Which side of the target the balloon appears on.
 * @param anchor The alignment point on the target that the arrow points to.
 * @param arrowOffset Distance of the arrow from the leading edge of the balloon. Must match the value passed to
 *   [GotItTooltipBalloonContainer].
 * @param padding Extra gap between the balloon arrow tip and the anchor point. Pushes the balloon away from the
 *   component it is anchored to.
 * @return a remembered [GotItTooltipBalloonPopupPositionProvider]
 */
@Composable
public fun rememberGotItTooltipBalloonPopupPositionProvider(
    gotItBalloonPosition: GotItBalloonPosition,
    anchor: Alignment,
    arrowOffset: Dp = 24.dp,
    padding: Dp = 0.dp,
): PopupPositionProvider {
    val density = LocalDensity.current
    val arrowOffsetPx = with(density) { arrowOffset.roundToPx() }
    val paddingPx = with(density) { padding.roundToPx() }
    return remember(gotItBalloonPosition, anchor, arrowOffsetPx, paddingPx) {
        GotItTooltipBalloonPopupPositionProvider(gotItBalloonPosition, anchor, arrowOffsetPx, paddingPx)
    }
}

private class GotItTooltipBalloonPopupPositionProvider(
    private val gotItBalloonPosition: GotItBalloonPosition,
    private val anchor: Alignment,
    private val arrowOffsetPx: Int,
    private val paddingPx: Int = 0,
) : PopupPositionProvider {
    @OptIn(InternalJewelApi::class)
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val base =
            calculateBalloonPosition(
                gotItBalloonPosition = gotItBalloonPosition,
                anchor = anchor,
                arrowOffsetPx = arrowOffsetPx,
                anchorBounds = anchorBounds,
                layoutDirection = layoutDirection,
                popupContentSize = popupContentSize,
            )
        if (paddingPx == 0) return base
        return when (gotItBalloonPosition) {
            GotItBalloonPosition.BELOW -> base.copy(y = base.y + paddingPx)
            GotItBalloonPosition.ABOVE -> base.copy(y = base.y - paddingPx)
            GotItBalloonPosition.START -> base.copy(x = base.x - paddingPx)
            GotItBalloonPosition.END -> base.copy(x = base.x + paddingPx)
        }
    }
}
