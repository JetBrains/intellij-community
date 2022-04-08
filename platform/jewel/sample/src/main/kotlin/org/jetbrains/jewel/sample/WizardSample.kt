package org.jetbrains.jewel.sample

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.components.Icon
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.GroupHeader
import org.jetbrains.jewel.theme.intellij.components.CheckboxRow
import org.jetbrains.jewel.theme.intellij.components.CircularProgressIndicator
import org.jetbrains.jewel.theme.intellij.components.IconButton
import org.jetbrains.jewel.theme.intellij.components.RadioButtonRow
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Tab
import org.jetbrains.jewel.theme.intellij.components.TabRow
import org.jetbrains.jewel.theme.intellij.components.Text
import org.jetbrains.jewel.theme.intellij.components.TextField
import org.jetbrains.jewel.theme.intellij.components.Tree
import org.jetbrains.jewel.theme.intellij.components.TreeLayout
import org.jetbrains.jewel.theme.intellij.components.asTree
import org.jetbrains.jewel.theme.intellij.components.rememberTabContainerState
import org.jetbrains.jewel.theme.toolbox.components.Divider
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Paths
import java.util.Optional

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
    IntelliJTheme(true) {
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
                modifier = modifier.height(64.dp).padding(10.dp),
                painter = painterResource("imageasset/android-studio.svg"),
                contentDescription = "logo",
                tint = Color.Unspecified
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
        OutputFilePanel(modifier)
    }
}

@Composable
fun DirectorySelection(modifier: Modifier = Modifier) {
    val outputDir = remember { mutableStateOf(System.getProperty("user.dir")) }
    Column(modifier.fillMaxSize()) {
        ResDirectoryLabelComboBox(outputDir = outputDir)
        OutputDirectoriesLabelTree(outputDir = outputDir)
    }
}

@Composable
fun ResDirectoryLabelComboBox(modifier: Modifier = Modifier, outputDir: MutableState<String>) {

    Row(modifier.height(30.dp).fillMaxWidth()) {
        Text(modifier = Modifier.padding(5.dp), text = "Res Directory:")
        TextField(modifier = Modifier.fillMaxSize().padding(5.dp), value = outputDir.value, onValueChange = {})
    }
}

@Composable
fun OutputDirectoriesLabelTree(modifier: Modifier = Modifier, outputDir: MutableState<String>) {
    Row(modifier.fillMaxSize()) {
        val tree = remember { mutableStateOf(Optional.empty<Tree<File>>()) }
        LaunchedEffect(true) {
            withContext(Dispatchers.IO) {
                tree.value = Optional.of(Paths.get(outputDir.value).asTree(true))
            }
        }

        if (tree.value.isEmpty) {
            Row {
                CircularProgressIndicator(
                    modifier = modifier.align(Alignment.CenterVertically)
                )
                Text(
                    modifier = modifier.padding(5.dp),
                    text = "Loading...",
                )
            }
        }
        else {
            Text(
                modifier = modifier.padding(5.dp),
                text = "Output Directories:" ,
            )
        }

        Box {
            val listState = rememberLazyListState()
            TreeLayout(
                modifier = Modifier.fillMaxWidth(),
                tree = tree.value.orElse(Tree(emptyList())),
                state = listState,
                onTreeChanged = { tree.value = Optional.of(it) },
                onTreeElementDoubleClick = { outputDir.value = it.data.absolutePath },
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
fun OutputFilePanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        Text(
            modifier = Modifier.padding(5.dp),
            text = "Output File"
        )
        TextFieldWithLabel(
            label = "File Type:",
            textFieldText = "PNG File",
            textFieldEnabled = false
        )
        TextFieldWithLabel(
            label = "Density:",
            textFieldText = "nodpi",
            textFieldEnabled = false
        )
        TextFieldWithLabel(
            label = "Size (dp):",
            textFieldText = "512x512",
            textFieldEnabled = false
        )
        TextFieldWithLabel(
            label = "Size (px):",
            textFieldText = "512x512",
            textFieldEnabled = false
        )
        Box(modifier.background(Color.Magenta).fillMaxSize().weight(1f))
    }
}

@Composable
fun TextFieldWithLabel(modifier: Modifier = Modifier, label: String, textFieldText: String, textFieldEnabled: Boolean) {
    Row(modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.Start) {
        Text(label)
        Spacer(modifier.width(5.dp))
        TextField(modifier = modifier.fillMaxWidth(), value = textFieldText, onValueChange = {}, enabled = textFieldEnabled)
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
    Row {
        val radioButtonModifier = Modifier.padding(end = 10.dp)
        RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) { Text("Image", modifier = radioButtonModifier) }
        RadioButtonRow(selected = assetType.value == AssetType.CLIP_ART, onClick = { assetType.value = AssetType.CLIP_ART }) { Text("Clip Art", modifier = radioButtonModifier) }
        RadioButtonRow(selected = assetType.value == AssetType.TEXT, onClick = { assetType.value = AssetType.TEXT }) { Text("Text", modifier = radioButtonModifier) }
    }
}

@Composable
fun BackgroundAssetTypeSelection(assetType: MutableState<AssetType>) {
    Row {
        val radioButtonModifier = Modifier.padding(end = 10.dp)
        RadioButtonRow(selected = assetType.value == AssetType.COLOR, onClick = { assetType.value = AssetType.COLOR }) { Text("Color", modifier = radioButtonModifier) }
        RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) { Text("Image", modifier = radioButtonModifier) }
    }
}

@Composable
fun AssetTypeSpecificOptions(assetType: AssetType, subLabelModifier: Modifier, rowModifier: Modifier) {
    when (assetType) {
        AssetType.IMAGE -> Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Path:", modifier = subLabelModifier)
            TextField(value = "some_path", onValueChange = {}, modifier = Modifier.fillMaxWidth())
        }
        AssetType.CLIP_ART -> {
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Text("Clip Art:", modifier = subLabelModifier)
            }
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Text("Color:", modifier = subLabelModifier)
            }
        }
        AssetType.TEXT -> {
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Text("Text:", modifier = subLabelModifier)
                TextField(value = "some text", onValueChange = {})
                // ComboBox()
            }
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Text("Color:", modifier = subLabelModifier)
            }
        }
        AssetType.COLOR -> Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Color:", modifier = subLabelModifier)
        }
    }
}

@Composable
fun CommonLayer(
    assetType: MutableState<AssetType>,
    assetTypeSelection: @Composable (assetType: MutableState<AssetType>) -> Unit,
    assetTypeSpecificOptions: @Composable (assetType: AssetType, subLabelModifier: Modifier, rowModifier: Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        val subLabelModifier = Modifier.width(100.dp).padding(start = 10.dp)
        val rowModifier = Modifier.height(30.dp)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Layer name:", modifier = Modifier.width(100.dp))
            val layerNameState = remember { mutableStateOf("layer name...") }
            TextField(value = layerNameState.value, onValueChange = { layerNameState.value = it }, modifier = Modifier.fillMaxWidth())
        }
        GroupHeader("Source Asset", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Asset Type:", modifier = subLabelModifier)
            assetTypeSelection(assetType)
        }
        assetTypeSpecificOptions(assetType.value, subLabelModifier, rowModifier)
        GroupHeader("Scaling", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            val trim = remember { mutableStateOf(true) }
            Text("Trim:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = trim.value, onClick = { trim.value = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !trim.value, onClick = { trim.value = false }) { Text("No", modifier = radioButtonModifier) }
        }
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Resize:", modifier = subLabelModifier)
        }
    }
}

@Composable
fun ForegroundLayer(modifier: Modifier) {
    val assetType = remember { mutableStateOf(AssetType.IMAGE) }
    CommonLayer(
        assetType = assetType,
        assetTypeSelection = { ForegroundAssetTypeSelection(it) },
        assetTypeSpecificOptions = { at: AssetType, subLabelModifier: Modifier, rowModifier: Modifier -> AssetTypeSpecificOptions(at, subLabelModifier, rowModifier) },
        modifier = modifier
    )
}

@Composable
fun BackgroundLayer(modifier: Modifier) {
    val assetType = remember { mutableStateOf(AssetType.COLOR) }
    CommonLayer(
        assetType = assetType,
        assetTypeSelection = { BackgroundAssetTypeSelection(it) },
        assetTypeSpecificOptions = { at: AssetType, subLabelModifier: Modifier, rowModifier: Modifier -> AssetTypeSpecificOptions(at, subLabelModifier, rowModifier) },
        modifier = modifier,
    )
}

@Composable
fun OptionsTab(modifier: Modifier) {
    Column(modifier) {
        val subLabelModifier = Modifier.width(100.dp).padding(start = 10.dp)
        val rowModifier = Modifier.height(30.dp)
        GroupHeader("Legacy Icon (API ≤ 25):", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            val generate = remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate.value, onClick = { generate.value = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate.value, onClick = { generate.value = false }) { Text("No", modifier = radioButtonModifier) }
        }
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Shape:", modifier = subLabelModifier)
        }
        GroupHeader("Round Icon (API = 25):", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            val generate = remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate.value, onClick = { generate.value = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate.value, onClick = { generate.value = false }) { Text("No", modifier = radioButtonModifier) }
        }
        GroupHeader("Google Play Store Icon", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            val generate = remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate.value, onClick = { generate.value = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate.value, onClick = { generate.value = false }) { Text("No", modifier = radioButtonModifier) }
        }
    }
}

enum class OptionTabs {
    FOREGROUND,
    BACKGROUND,
    OPTIONS
}

@Composable
fun FirstPage(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.width(400.dp).padding(all = 20.dp)) {
            Column {
                val labelModifier = Modifier.width(100.dp)
                Row(Modifier.height(30.dp)) {
                    Text("Icon type:", modifier = labelModifier.align(alignment = Alignment.CenterVertically))
                    // TextField(value = layerNameState.value, onValueChange = { })
                }
                Row(Modifier.height(30.dp)) {
                    Text("Name:", modifier = labelModifier.align(Alignment.CenterVertically))
                    TextField(value = "ic_launcher", onValueChange = { }, modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically))
                }
                val tabState = rememberTabContainerState(OptionTabs.FOREGROUND)
                TabRow(tabState) {
                    Tab(OptionTabs.FOREGROUND) { Text("Foreground Layer") }
                    Tab(OptionTabs.BACKGROUND) { Text("Background Layer") }
                    Tab(OptionTabs.OPTIONS) { Text("Options") }
                }
                Divider(orientation = Orientation.Horizontal)
                val tabContentModifier = Modifier.padding(all = 10.dp)
                when (tabState.selectedKey) {
                    OptionTabs.FOREGROUND -> ForegroundLayer(tabContentModifier)
                    OptionTabs.BACKGROUND -> BackgroundLayer(tabContentModifier)
                    OptionTabs.OPTIONS -> OptionsTab(tabContentModifier)
                }
            }
        }
        Box { }
    }
}
