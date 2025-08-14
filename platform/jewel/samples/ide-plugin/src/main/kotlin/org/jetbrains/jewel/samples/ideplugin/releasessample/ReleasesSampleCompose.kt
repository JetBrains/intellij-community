package org.jetbrains.jewel.samples.ideplugin.releasessample

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import icons.JewelIcons
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.typography

@Composable
internal fun ReleasesSampleCompose(project: Project) {
    var selectedItem: ContentItem? by remember { mutableStateOf(null) }
    HorizontalSplitLayout(
        first = {
            LeftColumn(
                project = project,
                modifier = Modifier.fillMaxSize(),
                onSelectedItemChange = { selectedItem = it },
            )
        },
        second = { RightColumn(selectedItem = selectedItem, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.fillMaxSize(),
        firstPaneMinWidth = 300.dp,
        secondPaneMinWidth = 300.dp,
        state = rememberSplitLayoutState(.3f),
    )
}

@Composable
private fun LeftColumn(project: Project, modifier: Modifier = Modifier, onSelectedItemChange: (ContentItem?) -> Unit) {
    val service = remember(project) { project.service<ReleasesSampleService>() }
    val currentContentSource by service.content.collectAsState()

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(4.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filter elements:")

            Spacer(Modifier.width(8.dp))

            SearchBar(service, Modifier.weight(1f))

            Spacer(Modifier.width(4.dp))

            OverflowMenu(currentContentSource) { service.setContentSource(it) }
        }

        val listState = rememberSelectableLazyListState()
        VerticallyScrollableContainer(listState.lazyListState, modifier) {
            SelectableLazyColumn(
                modifier = Modifier.fillMaxSize(),
                selectionMode = SelectionMode.Single,
                state = listState,
                onSelectedIndexesChange = {
                    val selectedItem =
                        if (it.isNotEmpty()) {
                            currentContentSource.items[it.first()]
                        } else {
                            null
                        }

                    onSelectedItemChange(selectedItem)
                },
            ) {
                items(
                    items = currentContentSource.items,
                    key = { it.key },
                    contentType = {
                        when (it) {
                            is ContentItem.AndroidRelease -> ItemType.AndroidRelease
                            is ContentItem.AndroidStudio -> ItemType.AndroidStudio
                        }
                    },
                ) {
                    ContentItemRow(it, isSelected, isActive) { newFilter -> service.filterContent(newFilter) }
                }
            }
        }
    }
}

@Composable
private fun ContentItemRow(item: ContentItem, isSelected: Boolean, isActive: Boolean, onTagClick: (String) -> Unit) {
    val color =
        when {
            isSelected && isActive -> retrieveColorOrUnspecified("List.selectionBackground")
            isSelected && !isActive -> retrieveColorOrUnspecified("List.selectionInactiveBackground")
            else -> Transparent
        }
    Row(
        modifier =
            Modifier.height(JewelTheme.globalMetrics.rowHeight)
                .background(color)
                .padding(horizontal = 4.dp)
                .padding(end = scrollbarContentSafePadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = item.displayText, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)

        val pointerModifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        when (item) {
            is ContentItem.AndroidRelease -> {
                ItemTag(
                    text = "API level ${item.apiLevel}",
                    backgroundColor = ReleaseChannel.Other.background.toComposeColor(),
                    foregroundColor = ReleaseChannel.Other.foreground.toComposeColor(),
                    modifier = pointerModifier.onClick { onTagClick(item.apiLevel.toString()) },
                )
            }

            is ContentItem.AndroidStudio -> {
                val channel = item.channel
                ItemTag(
                    text = channel.name.lowercase(),
                    backgroundColor = channel.background.toComposeColor(),
                    foregroundColor = channel.foreground.toComposeColor(),
                    modifier = pointerModifier.onClick { onTagClick(item.channel.name) },
                )
            }
        }
    }
}

@Composable
private fun ItemTag(
    text: String,
    backgroundColor: Color,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    padding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
) {
    Text(
        text = text,
        style = JewelTheme.typography.medium,
        color = foregroundColor,
        modifier = modifier.background(backgroundColor, shape).padding(padding),
    )
}

private enum class ItemType {
    AndroidRelease,
    AndroidStudio,
}

@Composable
private fun SearchBar(service: ReleasesSampleService, modifier: Modifier = Modifier) {
    val filterText by service.filter.collectAsState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val state = rememberTextFieldState(filterText)
    LaunchedEffect(state) { snapshotFlow { state.text }.collect { service.filterContent(it.toString()) } }
    TextField(
        state = state,
        modifier = modifier.focusRequester(focusRequester),
        leadingIcon = {
            Icon(AllIconsKeys.Actions.Find, contentDescription = "Find icon", Modifier.padding(end = 8.dp))
        },
        trailingIcon = {
            if (filterText.isNotBlank()) {
                CloseIconButton(service)
            }
        },
    )
}

@Composable
private fun CloseIconButton(service: ReleasesSampleService) {
    val interactionSource = remember { MutableInteractionSource() }
    var hovered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is HoverInteraction.Enter -> hovered = true
                is HoverInteraction.Exit -> hovered = false
            }
        }
    }

    Icon(
        key = if (hovered) AllIconsKeys.Actions.CloseHovered else AllIconsKeys.Actions.Close,
        contentDescription = "Clear",
        modifier =
            Modifier.pointerHoverIcon(PointerIcon.Default).clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) {
                service.resetFilter()
            },
    )
}

@Composable
private fun OverflowMenu(currentContentSource: ContentSource<*>, onContentSourceChange: (ContentSource<*>) -> Unit) {
    var menuVisible by remember { mutableStateOf(false) }

    // Emulates Swing actions that pop up menus � they stay pressed while the menu is open
    IconButton(
        modifier =
            Modifier.fillMaxHeight().thenIf(menuVisible) {
                background(
                        color = JewelTheme.iconButtonStyle.colors.backgroundPressed,
                        shape = RoundedCornerShape(JewelTheme.iconButtonStyle.metrics.cornerSize),
                    )
                    .border(
                        width = JewelTheme.iconButtonStyle.metrics.borderWidth,
                        color = JewelTheme.iconButtonStyle.colors.backgroundPressed,
                        shape = RoundedCornerShape(JewelTheme.iconButtonStyle.metrics.cornerSize),
                    )
            },
        onClick = { menuVisible = !menuVisible },
    ) {
        Icon(key = AllIconsKeys.Ide.Notification.Gear, contentDescription = "Select data source")
    }

    val contentSources = remember { listOf(AndroidStudioReleases, AndroidReleases) }

    if (menuVisible) {
        PopupMenu(
            onDismissRequest = {
                menuVisible = false
                true
            },
            horizontalAlignment = Alignment.End,
            content = {
                items(
                    contentSources,
                    isSelected = {
                        // TODO fix this check once the "selected" check works correctly;
                        //  the selected flag should be set on mouse hover/keyboard navigation
                        it.isSameAs(currentContentSource)
                    },
                    onItemClick = onContentSourceChange,
                ) {
                    Row(
                        modifier = Modifier.height(JewelTheme.globalMetrics.rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (it.isSameAs(currentContentSource)) {
                            Icon(AllIconsKeys.Actions.Checked, "Checked icon")
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }

                        Text(it.displayName)
                    }
                }
            },
        )
    }
}

@Composable
private fun RightColumn(selectedItem: ContentItem?, modifier: Modifier) {
    if (selectedItem == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Nothing to see here", color = JBUI.CurrentTheme.Label.disabledForeground().toComposeColor())
        }
    } else {
        VerticallyScrollableContainer(modifier = modifier) {
            Column(verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.Start) {
                val imagePath = selectedItem.imagePath
                if (imagePath != null) {
                    ReleaseImage(imagePath)
                }

                ItemDetailsText(selectedItem)
            }
        }
    }
}

@Composable
private fun ReleaseImage(imagePath: String) {
    val painterProvider = rememberResourcePainterProvider(imagePath, JewelIcons::class.java)
    val painter by painterProvider.getPainter()
    val transition = rememberInfiniteTransition("HoloFoil")
    val offset by
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(durationMillis = 2.seconds.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            "holoFoil offset",
        )
    var isHovered by remember { mutableStateOf(false) }
    var applyModifier by remember { mutableStateOf(false) }
    val intensity by animateFloatAsState(if (isHovered) 1f else 0f, animationSpec = tween(300))

    val scope = rememberCoroutineScope()

    Image(
        painter = painter,
        contentDescription = null,
        modifier =
            Modifier.fillMaxWidth()
                .sizeIn(minHeight = 150.dp, maxHeight = 250.dp)
                .onHover { newIsHovered ->
                    scope.launch {
                        isHovered = newIsHovered
                        if (!newIsHovered) delay(300)
                        applyModifier = newIsHovered
                    }
                }
                .thenIf(applyModifier) { holoFoil(offset, intensity) },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ItemDetailsText(selectedItem: ContentItem) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(selectedItem.displayText, style = JewelTheme.typography.h1TextStyle)

        val formatter = remember(Locale.current) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
        val releaseDate = selectedItem.releaseDate
        if (releaseDate != null) {
            Text(
                text = "Released on ${formatter.format(releaseDate.toJavaLocalDate())}",
                style = JewelTheme.typography.medium,
                color = JewelTheme.globalColors.text.info,
            )
        }

        Spacer(Modifier.height(20.dp))

        when (selectedItem) {
            is ContentItem.AndroidRelease -> AndroidReleaseDetails(selectedItem)
            is ContentItem.AndroidStudio -> AndroidStudioReleaseDetails(selectedItem)
        }
    }
}

@Composable
private fun AndroidReleaseDetails(item: ContentItem.AndroidRelease) {
    TextWithLabel("Codename:", item.codename ?: "N/A")
    TextWithLabel("Version:", item.versionName)
    TextWithLabel("API level:", item.apiLevel.toString())
}

@Composable
private fun AndroidStudioReleaseDetails(item: ContentItem.AndroidStudio) {
    TextWithLabel("Channel:", item.channel.name)
    TextWithLabel("Version:", item.versionName)
    TextWithLabel("IntelliJ Platform version:", item.platformVersion)
    TextWithLabel("IntelliJ Platform build:", item.platformBuild)
    TextWithLabel("Full build number:", item.build)
}

@Composable
private fun TextWithLabel(labelText: String, valueText: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(labelText)
        Text(valueText, style = JewelTheme.typography.regular.copy(fontWeight = FontWeight.Bold))
    }
}
