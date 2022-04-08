package org.jetbrains.jewel.sample

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.CheckboxRow
import org.jetbrains.jewel.theme.intellij.components.IconButton
import org.jetbrains.jewel.theme.intellij.components.RadioButtonRow
import org.jetbrains.jewel.theme.intellij.components.Separator
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Tab
import org.jetbrains.jewel.theme.intellij.components.TabRow
import org.jetbrains.jewel.theme.intellij.components.Text
import java.awt.event.WindowEvent
import org.jetbrains.jewel.theme.intellij.components.Tree
import org.jetbrains.jewel.theme.intellij.components.TreeLayout
import org.jetbrains.jewel.theme.intellij.components.asTree
import org.jetbrains.jewel.theme.intellij.components.rememberTabContainerState
import org.jetbrains.jewel.theme.toolbox.components.Divider
import java.nio.file.Paths

private const val WIZARD_PAGE_COUNT = 2

fun main() {
    singleWindowApplication {
        Wizard(onFinish = {
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        })
    }
}

@Composable
fun Wizard(onFinish: () -> Unit) {
    IntelliJThemeDark {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                val currentPage = mutableStateOf(1) // 1-based
                WizardHeader(currentPage = currentPage)
                WizardMainContent(
                    modifier = Modifier.weight(1f),
                    currentPage = currentPage
                )
                WizardFooter(currentPage = currentPage, onFinish = onFinish)
            }
        }
    }
}

@Composable
fun WizardHeader(modifier: Modifier = Modifier, currentPage: MutableState<Int>) {
    Box(modifier.background(Color(0xFF616161)).height(100.dp).fillMaxWidth()) {
        Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = modifier.height(74.dp).padding(10.dp),
                painter = painterResource("imageasset/android-studio.svg"),
                contentDescription = "logo",
                tint = Color.Unspecified // FIXME: tint is being applied regardless
            )
            Text(
                text = when (currentPage.value) {
                    1 -> "Configure Image Asset"
                    2 ->  "Confirm Icon Path"
                    else -> "Assets Wizard"
                },
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun WizardMainContent(modifier: Modifier = Modifier, currentPage: MutableState<Int>) {
    if (currentPage.value == 1) {
        FirstPage(modifier.fillMaxWidth().fillMaxHeight())
    }
    else if (currentPage.value == 2) {
        ConfirmIconPathPage(modifier)
    }
}

@Composable
fun WizardFooter(modifier: Modifier = Modifier, currentPage: MutableState<Int>, onFinish: () -> Unit) {
    Box(modifier.height(50.dp).fillMaxWidth()) {
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HelpIcon()
            WizardControls(currentPage = currentPage, onFinish = onFinish)
        }
    }
}

@Composable
fun ConfirmIconPathPage(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        DirectorySelection(modifier)
        Box(modifier.background(Color.Magenta).fillMaxSize())
    }
}

@Composable
fun DirectorySelection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ResDirectoryLabelComboBox()
        OutputDirectoriesLabelTree()
    }
}

@Composable
fun ResDirectoryLabelComboBox(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Yellow).height(30.dp).fillMaxWidth())
}

@Composable
fun OutputDirectoriesLabelTree(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Text(
            modifier = modifier.padding(5.dp),
            text = "Output Directories:",
            color = Color.Black
        )
        var tree by remember { mutableStateOf(Paths.get(System.getProperty("user.dir")).asTree(true)) }

        Box {
            val listState = rememberLazyListState()
            TreeLayout(
                modifier = Modifier.fillMaxWidth(),
                tree = tree,
                state = listState,
                onTreeChanged = { tree = it },
                onTreeElementDoubleClick = {},
                rowContent = {
                    val text: String = when (it) {
                        is Tree.Element.Leaf -> it.data.name
                        is Tree.Element.Node -> "[${it.data.name}]"
                    }
                    Text(modifier = Modifier.fillMaxWidth(), text = text, softWrap = false)
                },
            )
            if (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size) {
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 2.dp),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }
        }
    }

}

@Composable
fun HelpIcon(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    IconButton(
        modifier = modifier,
        onClick = { uriHandler.openUri("https://developer.android.com/studio/write/image-asset-studio") },
    ) {
        Icon(
            Icons.Default.Info, // Help icon requires adding a new dependency, so we're using info instead
            contentDescription = "help button",
            tint = Color.Unspecified // FIXME: tint is being applied regardless
        )
    }
}

@Composable
fun WizardControls(modifier: Modifier = Modifier, currentPage: MutableState<Int>, onFinish: () -> Unit) {
    Row(modifier) {
        Button(onClick = onFinish) {
            Text("Cancel")
        }
        Button(onClick = { currentPage.value-- }, enabled = currentPage.value > 1) {
            Text("Previous")
        }
        Button(onClick = { currentPage.value++ }, enabled = currentPage.value < WIZARD_PAGE_COUNT) {
            Text("Next")
        }
        Button(onClick = onFinish, enabled = currentPage.value == WIZARD_PAGE_COUNT) {
            Text("Finish")
        }
    }
}

enum class AssetType {
    IMAGE,
    CLIP_ART,
    TEXT,
    COLOR
}

@Composable
fun ForegroundAssetTypeSelection(assetType: MutableState<AssetType>) {
    RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) { Text("Image") }
    RadioButtonRow(selected = assetType.value == AssetType.CLIP_ART, onClick = { assetType.value = AssetType.CLIP_ART }) { Text("Clip Art") }
    RadioButtonRow(selected = assetType.value == AssetType.TEXT, onClick = { assetType.value = AssetType.TEXT }) { Text("Text") }
}

@Composable
fun BackgroundAssetTypeSelection(assetType: MutableState<AssetType>) {
    RadioButtonRow(selected = assetType.value == AssetType.COLOR, onClick = { assetType.value = AssetType.COLOR }) { Text("Color") }
    RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) { Text("Image") }
}

@Composable
fun AssetTypeSpecificOptions(assetType: AssetType, subLabelModifier: Modifier) {
    when (assetType) {
        AssetType.IMAGE -> Row {
            Text("Path:", modifier = subLabelModifier)
        }
        AssetType.CLIP_ART -> {
            Row {
                Text("Clip Art:", modifier = subLabelModifier)
            }
            Row {
                Text("Color:", modifier = subLabelModifier)
            }
        }
        AssetType.TEXT -> {
            Row {
                Text("Text:", modifier = subLabelModifier)
            }
            Row {
                Text("Color:", modifier = subLabelModifier)
            }
        }
        AssetType.COLOR -> Row {
            Text("Color:", modifier = subLabelModifier)
        }
    }
}

@Composable
fun CommonLayer(
    assetType: MutableState<AssetType>,
    assetTypeSelection: @Composable (assetType: MutableState<AssetType>) -> Unit,
    assetTypeSpecificOptions: @Composable (assetType: AssetType, subLabelModifier: Modifier) -> Unit
) {
    Column(modifier = Modifier.height(200.dp).width(300.dp)) {
        val labelColWidth = 100.dp
        val subLabelsPadding = 10.dp
        Row {
            Text("Layer name:", modifier = Modifier.width(labelColWidth))
            val layerNameState = remember { mutableStateOf("layer name...") }
            // TextField(layerNameState.value, { layerNameState.value = it })
        }
        Row {
            Text("Source Asset")
            Separator(orientation = Orientation.Horizontal, modifier = Modifier.align(alignment = Alignment.CenterVertically))
        }
        Row {
            Text("Asset Type:", modifier = Modifier.width(labelColWidth).padding(start = subLabelsPadding))
            // RadioButton("Image", select = false, onClick = {}) // (checked = false, onCheckedChange = {}) { Text("Image" ) }
            assetTypeSelection(assetType)
        }
        assetTypeSpecificOptions(assetType.value, Modifier.width(labelColWidth).padding(start = subLabelsPadding))
        Row {
            Text("Scaling")
            Separator(orientation = Orientation.Horizontal, modifier = Modifier.align(alignment = Alignment.CenterVertically))
        }
        Row {
            Text("Trim:", modifier = Modifier.width(labelColWidth).padding(start = subLabelsPadding))
            CheckboxRow(checked = false, onCheckedChange = {}) { Text("Yes" ) }
            CheckboxRow(checked = false, onCheckedChange = {}) { Text("No") }
        }
        Row {
            Text("Resize:", modifier = Modifier.width(labelColWidth).padding(start = subLabelsPadding))
        }
    }
}

@Composable
fun ForegroundLayer() {
    val assetType = remember { mutableStateOf(AssetType.IMAGE) }
    CommonLayer(
        assetType,
        { ForegroundAssetTypeSelection(it) },
        { at: AssetType, subLabelModifier: Modifier -> AssetTypeSpecificOptions(at, subLabelModifier) }
    )
}

@Composable
fun BackgroundLayer() {
    val assetType = remember { mutableStateOf(AssetType.COLOR) }
    CommonLayer(
        assetType,
        { BackgroundAssetTypeSelection(it) },
        { at: AssetType, subLabelModifier: Modifier -> AssetTypeSpecificOptions(at, subLabelModifier) }
    )
}

enum class OptionTabs {
    FOREGROUND,
    BACKGROUND,
    OPTIONS
}

@Composable
fun FirstPage(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.width(300.dp)) {
            Column {
                val tabState = rememberTabContainerState(OptionTabs.FOREGROUND)
                TabRow(tabState) {
                    Tab(OptionTabs.FOREGROUND) { Text("Foreground Layer") }
                    Tab(OptionTabs.BACKGROUND) { Text("Background Layer") }
                    Tab(OptionTabs.OPTIONS) { Text("Options") }
                }
                Divider(orientation = Orientation.Horizontal)
                when (tabState.selectedKey) {
                    OptionTabs.FOREGROUND -> ForegroundLayer()
                    OptionTabs.BACKGROUND -> BackgroundLayer()
                    else -> {
                    }
                }
            }
        }
        Box { }
    }
}
