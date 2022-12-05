package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.components.Icon
import org.jetbrains.jewel.isMacOs
import org.jetbrains.jewel.themes.darcula.IntelliJTheme
import org.jetbrains.jewel.themes.darcula.components.Button
import org.jetbrains.jewel.themes.darcula.components.CheckboxRow
import org.jetbrains.jewel.themes.darcula.components.CircularProgressIndicator
import org.jetbrains.jewel.themes.darcula.components.Divider
import org.jetbrains.jewel.themes.darcula.components.GroupHeader
import org.jetbrains.jewel.themes.darcula.components.IconButton
import org.jetbrains.jewel.themes.darcula.components.RadioButtonRow
import org.jetbrains.jewel.themes.darcula.components.Slider
import org.jetbrains.jewel.themes.darcula.components.Surface
import org.jetbrains.jewel.themes.darcula.components.Tab
import org.jetbrains.jewel.themes.darcula.components.TabRow
import org.jetbrains.jewel.themes.darcula.components.Text
import org.jetbrains.jewel.themes.darcula.components.TextField
import org.jetbrains.jewel.themes.darcula.components.Tree
import org.jetbrains.jewel.themes.darcula.components.TreeLayout
import org.jetbrains.jewel.themes.darcula.components.asTree
import org.jetbrains.jewel.themes.darcula.components.rememberTabContainerState
import org.jetbrains.jewel.themes.darcula.pxToDp
import org.jetbrains.jewel.themes.darcula.styles.ButtonStyle
import org.jetbrains.jewel.themes.darcula.styles.IntelliJButtonStyleVariations
import org.jetbrains.jewel.themes.darcula.styles.SliderStyle
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Paths
import java.util.Optional

fun main() = application {
    val state = rememberWindowState(size = DpSize(1020.dp, 680.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Asset Studio",
        icon = useResource("images/android-head.svg") { loadSvgPainter(it, Density(1f)) },
        state = state
    ) {
        Wizard(onFinish = {
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        })

        window.minimumSize = Dimension(814, 607)
    }
}

private enum class WizardPage(
    val index: Int,
    val title: String,
) {

    CONFIGURE(index = 0, title = "Configure Image Asset"),
    CONFIRM(index = 1, title = "Confirm Icon Path");

    fun nextPage() = values().find { it.index == index + 1 }

    fun previousPage() = values().find { it.index == index - 1 }
}

@Composable
fun Wizard(onFinish: () -> Unit) {
    IntelliJTheme(true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                var currentPage by remember { mutableStateOf(WizardPage.CONFIGURE) }
                WizardHeader(currentPage, Modifier.fillMaxWidth())

                WizardMainContent(
                    currentPage = currentPage,
                    modifier = Modifier.weight(1f)
                )

                WizardFooter(
                    currentPage = currentPage,
                    onPageChange = { currentPage = it },
                    onFinish = onFinish,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(currentPage: WizardPage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(if (IntelliJTheme.palette.isLight) 0xFF616161 else 0xFF4B4B4B))
            .height(112.dp)
            .padding(horizontal = 16.pxToDp(), vertical = 20.pxToDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.pxToDp(), Alignment.Start)
    ) {
        Icon(
            painter = painterResource("images/android-studio.svg"),
            contentDescription = "logo",
            tint = Color.Unspecified
        )
        Text(
            text = currentPage.title,
            fontSize = 24.sp,
            color = Color.White
        )
    }
}

@Composable
private fun WizardMainContent(currentPage: WizardPage, modifier: Modifier = Modifier) {
    when (currentPage) {
        WizardPage.CONFIGURE -> ConfigurePage(modifier)
        WizardPage.CONFIRM -> ConfirmIconPathPage(modifier)
    }
}

@Composable
private fun WizardFooter(
    currentPage: WizardPage,
    modifier: Modifier = Modifier,
    onPageChange: (WizardPage) -> Unit,
    onFinish: () -> Unit
) {
    Box(
        modifier = modifier.height(47.pxToDp())
    ) {
        Divider(color = Color(if (IntelliJTheme.palette.isLight) 0xFFC0C0C0 else 0xFF323232))

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.pxToDp(), vertical = 12.pxToDp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.pxToDp())
        ) {
            HelpButton(Modifier.size(24.pxToDp(), 24.pxToDp()))

            Spacer(Modifier.weight(1f))

            WizardControls(
                currentPage = currentPage,
                onPageChange = onPageChange,
                onFinish = onFinish,
            )
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
    Row(
        modifier
            .height(30.dp)
            .fillMaxWidth()
    ) {
        Text(modifier = Modifier.padding(5.dp), text = "Res Directory:")
        TextField(modifier = Modifier
            .fillMaxSize()
            .padding(5.dp), value = outputDir.value, onValueChange = {})
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
        } else {
            Text(
                modifier = modifier.padding(5.dp),
                text = "Output Directories:",
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
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 2.dp),
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
        Box(
            modifier
                .background(Color.Magenta)
                .fillMaxSize()
                .weight(1f)
        )
    }
}

@Composable
fun TextFieldWithLabel(modifier: Modifier = Modifier, label: String, textFieldText: String, textFieldEnabled: Boolean) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(5.dp), horizontalArrangement = Arrangement.Start
    ) {
        Text(label)
        Spacer(modifier.width(5.dp))
        TextField(modifier = modifier.fillMaxWidth(), value = textFieldText, onValueChange = {}, enabled = textFieldEnabled)
    }
}

@Composable
fun HelpButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    IconButton(
        modifier = modifier,
        onClick = { uriHandler.openUri("https://developer.android.com/studio/write/image-asset-studio") },
        style = ButtonStyle(
            IntelliJTheme.palette,
            IntelliJTheme.metrics,
            IntelliJTheme.typography.button,
            shape = CircleShape,
            contentPadding = PaddingValues(),
            minSize = DpSize(0.dp, 0.dp)
        )
    ) {
        val helpIconPath = if (IntelliJTheme.palette.isLight) "images/help.svg" else "images/help_dark.svg"
        Icon(
            painterResource(helpIconPath), // Help icon requires adding a new dependency, so we're using info instead
            contentDescription = "Show help contents",
            tint = IntelliJTheme.typography.button.color
        )
    }
}

@Composable
private fun WizardControls(
    currentPage: WizardPage,
    modifier: Modifier = Modifier,
    onPageChange: (WizardPage) -> Unit,
    onFinish: () -> Unit
) {
    fun changePage(newPageOrNull: WizardPage?) {
        checkNotNull(newPageOrNull) { "The page we're trying to navigate to is null" }
        onPageChange(newPageOrNull)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.pxToDp())
    ) {
        val buttonHeight = 26.pxToDp()
        val buttonStyle = ButtonStyle(
            IntelliJTheme.palette,
            IntelliJTheme.metrics,
            IntelliJTheme.typography.button,
            contentPadding = PaddingValues(),
            minSize = DpSize(72.pxToDp(), buttonHeight)
        )

        val previousPage = currentPage.previousPage()
        val nextPage = currentPage.nextPage()

        @Composable
        fun CancelButton() {
            Button(onClick = onFinish, style = buttonStyle) {
                Text("Cancel")
            }
        }

        @Composable
        fun PreviousButton() {
            Button(onClick = { changePage(previousPage) }, enabled = previousPage != null, style = buttonStyle) {
                Text("Previous")
            }
        }

        @Composable
        fun NextButton() {
            Button(
                onClick = { changePage(nextPage) },
                variation = IntelliJButtonStyleVariations.DefaultButton,
                enabled = nextPage != null,
                style = buttonStyle
            ) {
                Text("Next", fontWeight = FontWeight.Bold)
            }
        }

        @Composable
        fun FinishButton() {
            Button(onClick = onFinish, enabled = nextPage == null, style = buttonStyle) {
                Text("Finish")
            }
        }

        if (isMacOs()) {
            CancelButton()
            PreviousButton()
            NextButton()
            FinishButton()
        } else {
            PreviousButton()
            NextButton()
            CancelButton()
            FinishButton()
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
        RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) {
            Text(
                "Image",
                modifier = radioButtonModifier
            )
        }
        RadioButtonRow(selected = assetType.value == AssetType.CLIP_ART, onClick = { assetType.value = AssetType.CLIP_ART }) {
            Text(
                "Clip Art",
                modifier = radioButtonModifier
            )
        }
        RadioButtonRow(selected = assetType.value == AssetType.TEXT, onClick = { assetType.value = AssetType.TEXT }) {
            Text(
                "Text",
                modifier = radioButtonModifier
            )
        }
    }
}

@Composable
fun BackgroundAssetTypeSelection(assetType: MutableState<AssetType>) {
    Row {
        val radioButtonModifier = Modifier.padding(end = 10.dp)
        RadioButtonRow(selected = assetType.value == AssetType.COLOR, onClick = { assetType.value = AssetType.COLOR }) {
            Text(
                "Color",
                modifier = radioButtonModifier
            )
        }
        RadioButtonRow(selected = assetType.value == AssetType.IMAGE, onClick = { assetType.value = AssetType.IMAGE }) {
            Text(
                "Image",
                modifier = radioButtonModifier
            )
        }
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
        val subLabelModifier =
            Modifier
                .width(100.dp)
                .padding(start = 10.dp)
        val rowModifier = Modifier.height(30.dp)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Layer name:", modifier = Modifier.width(100.dp))
            var layerNameState by remember { mutableStateOf("layer name...") }
            TextField(value = layerNameState, onValueChange = { layerNameState = it }, modifier = Modifier.fillMaxWidth())
        }
        GroupHeader("Source Asset", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Asset Type:", modifier = subLabelModifier)
            assetTypeSelection(assetType)
        }
        assetTypeSpecificOptions(assetType.value, subLabelModifier, rowModifier)
        GroupHeader("Scaling", rowModifier)
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            var trim by remember { mutableStateOf(true) }
            Text("Trim:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = trim, onClick = { trim = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !trim, onClick = { trim = false }) { Text("No", modifier = radioButtonModifier) }
        }
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Resize:", modifier = subLabelModifier)
            val sliderValue = remember { mutableStateOf(0) }
            Slider(
                sliderValue.value,
                style = SliderStyle(
                    palette = IntelliJTheme.palette,
                    typography = IntelliJTheme.typography,
                    minorTickSpacing = 20,
                    majorTickSpacing = 0,
                    paintTicks = true
                ),
                min = 0,
                max = 400,
                modifier = Modifier.weight(1.0f)
            ) { sliderValue.value = it }
            Text("${sliderValue.value}%", modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun ForegroundLayer(modifier: Modifier) {
    val assetType = remember { mutableStateOf(AssetType.IMAGE) }
    CommonLayer(
        assetType = assetType,
        assetTypeSelection = { ForegroundAssetTypeSelection(it) },
        assetTypeSpecificOptions = { at: AssetType, subLabelModifier: Modifier, rowModifier: Modifier ->
            AssetTypeSpecificOptions(
                at,
                subLabelModifier,
                rowModifier
            )
        },
        modifier = modifier
    )
}

@Composable
fun BackgroundLayer(modifier: Modifier) {
    val assetType = remember { mutableStateOf(AssetType.COLOR) }
    CommonLayer(
        assetType = assetType,
        assetTypeSelection = { BackgroundAssetTypeSelection(it) },
        assetTypeSpecificOptions = { at: AssetType, subLabelModifier: Modifier, rowModifier: Modifier ->
            AssetTypeSpecificOptions(
                at,
                subLabelModifier,
                rowModifier
            )
        },
        modifier = modifier,
    )
}

@Composable
fun OptionsTab(modifier: Modifier) {
    Column(modifier) {
        val subLabelModifier =
            Modifier
                .width(100.dp)
                .padding(start = 10.dp)
        val rowModifier = Modifier.height(30.dp)
        GroupHeader("Legacy Icon (API ≤ 25):", rowModifier)

        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            var generate by remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate, onClick = { generate = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate, onClick = { generate = false }) { Text("No", modifier = radioButtonModifier) }
        }

        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Shape:", modifier = subLabelModifier)
        }

        GroupHeader("Round Icon (API = 25):", rowModifier)

        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            var generate by remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate, onClick = { generate = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate, onClick = { generate = false }) { Text("No", modifier = radioButtonModifier) }
        }

        GroupHeader("Google Play Store Icon", rowModifier)

        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            var generate by remember { mutableStateOf(true) }
            Text("Generate:", modifier = subLabelModifier)
            val radioButtonModifier = Modifier.padding(end = 10.dp)
            RadioButtonRow(selected = generate, onClick = { generate = true }) { Text("Yes", modifier = radioButtonModifier) }
            RadioButtonRow(selected = !generate, onClick = { generate = false }) { Text("No", modifier = radioButtonModifier) }
        }
    }
}

enum class OptionTabs {
    FOREGROUND,
    BACKGROUND,
    OPTIONS
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun ConfigurePage(modifier: Modifier = Modifier) {
    val minSizeLeft = 354.pxToDp()
    val minSizeRight = 391.pxToDp()

    HorizontalSplitPane(modifier = modifier.padding(24.pxToDp())) {
        first(minSize = minSizeLeft) {
            Column {
                val labelModifier = Modifier.width(80.pxToDp())

                Row(Modifier.height(30.dp)) {
                    Text("Icon type:", modifier = labelModifier.align(alignment = Alignment.CenterVertically))
                    // TextField(value = layerNameState.value, onValueChange = { })
                }

                Row(Modifier.height(30.dp)) {
                    Text("Name:", modifier = labelModifier.align(Alignment.CenterVertically))
                    TextField(
                        value = "ic_launcher", onValueChange = { }, modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterVertically)
                    )
                }
                val tabState = rememberTabContainerState(OptionTabs.FOREGROUND)
                TabRow(tabState) {
                    Tab(OptionTabs.FOREGROUND) { Text("Foreground Layer") }
                    Tab(OptionTabs.BACKGROUND) { Text("Background Layer") }
                    Tab(OptionTabs.OPTIONS) { Text("Options") }
                }
                Divider(orientation = Orientation.Horizontal)
                val tabContentModifier = Modifier.padding(all = 10.dp)
                when (tabState.selectedKey!!) {
                    OptionTabs.FOREGROUND -> ForegroundLayer(tabContentModifier)
                    OptionTabs.BACKGROUND -> BackgroundLayer(tabContentModifier)
                    OptionTabs.OPTIONS -> OptionsTab(tabContentModifier)
                }
            }
        }

        second(minSize = minSizeRight) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupHeader("Preview", modifier = Modifier.weight(1.0f))
                    TextField(value = "", onValueChange = {}, modifier = Modifier.width(50.dp))
                    val showSafeZone = remember { mutableStateOf(true) }
                    val showGrid = remember { mutableStateOf(false) }
                    CheckboxRow(
                        checked = showSafeZone.value,
                        onCheckedChange = { showSafeZone.value = it },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            "Show safe zone",
                        )
                    }
                    CheckboxRow(checked = showGrid.value, onCheckedChange = { showGrid.value = it }) {
                        Text(
                            "Show grid",
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(20.dp)
                        .background(color = Color.Green)
                        .fillMaxSize()
                )
            }
        }

        splitter {
            visiblePart {
                Box(
                    Modifier
                        .width(10.pxToDp())
                        .fillMaxHeight()
                )
            }
            handle {
                Box(
                    Modifier
                        .markAsHandle()
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .width(10.pxToDp())
                        .fillMaxHeight()
                )
            }
        }
    }
}
