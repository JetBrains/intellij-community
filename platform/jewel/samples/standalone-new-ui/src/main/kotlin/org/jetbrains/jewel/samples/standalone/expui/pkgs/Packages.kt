package org.jetbrains.jewel.samples.standalone.expui.pkgs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.themes.expui.desktop.window.JBWindow
import org.jetbrains.jewel.themes.expui.standalone.control.Checkbox
import org.jetbrains.jewel.themes.expui.standalone.control.Chip
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownLink
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownMenu
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownMenuItem
import org.jetbrains.jewel.themes.expui.standalone.control.Icon
import org.jetbrains.jewel.themes.expui.standalone.control.Label
import org.jetbrains.jewel.themes.expui.standalone.control.Link
import org.jetbrains.jewel.themes.expui.standalone.control.LinkColors
import org.jetbrains.jewel.themes.expui.standalone.control.TextField
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.theme.DarkTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import kotlin.random.Random
import kotlin.system.exitProcess

fun main() = application {
    var isDark by remember { mutableStateOf(true) }
    val theme = if (isDark) {
        DarkTheme
    } else {
        LightTheme
    }
    val pkgsViewModel = remember { PackageSearchViewModel() }
    JBWindow(
        title = "Jewel New UI Sample",
        theme = theme,
        state = rememberWindowState(size = DpSize(1600.dp, 700.dp)),
        onCloseRequest = {
            exitApplication()
            exitProcess(0)
        }
    ) {
        Column(Modifier.padding(4.dp)) {
            Row(Modifier.focusable()) {
                Label("Dark theme:")
                Checkbox(checked = isDark, onCheckedChange = { isDark = it })
            }
            PackageSearchBox(
                textSearchState = pkgsViewModel.inputText,
                onTextValueChange = { pkgsViewModel.inputText = it },
                availableFilters = pkgsViewModel.searchFilters,
                searchResultsStateList = pkgsViewModel.searchResults,
                onSearchResultClick = { pkgsViewModel.selectedResult.value = it },
                selectedModule = pkgsViewModel.selectedModule,
                addedModules = pkgsViewModel.addedModules.value
            )
        }
    }
}

@Suppress("MagicNumber")
sealed class Target(
    val shortName: String,
    val description: String = "",
    val subTargets: List<Target> = emptyList(),
    val bgColor: Color = Color(109, 109, 255, 255)
) {

    @Composable
    fun composable() {
        val fontColor = remember { if (bgColor.red * 0.299 + bgColor.green * 1 + bgColor.blue * 0.114 > 0.98) Color.Black else Color.White }
        Chip(
            bgColor,
            toolTipContent = {
                Column(Modifier.padding(vertical = 8.dp).fillMaxWidth(.3f)) {
                    Label(
                        modifier = Modifier.padding(16.dp),
                        text = description.ifEmpty {
                            "Contrary to popular belief, Lorem Ipsum is not simply random text. It has roots in a piece " +
                                "of classical Latin literature from 45 BC, making it over 2000 years old"
                        }
                    )
                    if (subTargets.isNotEmpty()) {
                        Label("Target Also:")
                        Row(Modifier.padding(8.dp)) {
                            subTargets.forEach { it.composable() }
                        }
                    }
                }
            }
        ) {
            Label(modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1, text = shortName, color = fontColor)
        }
    }

    object IOSX64 : Target("iosX64", "")
    object IOSARM64 : Target("iosARM64", "")
    object WATCHARM32 : Target("watchOS32", "")
    object APPLE : Target("apple", "", listOf(IOSX64, IOSARM64, WATCHARM32))
    object ANDROID : Target("android", "", bgColor = Color(121, 191, 45, 255))
    object JS : Target("js", "", bgColor = Color(255, 177, 0, 255))
    object COMMON : Target("common", "", listOf(IOSX64, IOSARM64, WATCHARM32, IOSX64, IOSARM64), bgColor = Color(117, 255, 255, 255))
    object JVM : Target("jvm", "", bgColor = Color(121, 191, 45, 255))

    @Suppress("unused")
    object MOBILE : Target("mobile", "", listOf(APPLE, ANDROID), bgColor = Color(109, 109, 255, 255))
}

class PackageSearchViewModel {

    val samplesForTest = 10

    var inputText by mutableStateOf("")

    val selectedModule by mutableStateOf("All Modules")
    val addedModules = mutableStateOf(pkgSearchResultGenerator(3))

    val searchFilters = mutableStateMapOf("OnlyStable" to false)
    val searchResults = mutableStateListOf<PKGSResult>()
    val selectedResult = mutableStateOf<PKGSResult?>(null)

    init {
        searchResults.addAll(pkgSearchResultGenerator(samplesForTest))
//        GlobalScope.launch {
//            val results = pkgSearchResultGenerator()
//            repeat(100) {
//                delay(1000)
//                searchResults.add(results.random())
//            }
//        }
    }
}

@Composable
fun PackageSearchBox(
    textSearchState: String,
    onTextValueChange: (String) -> Unit = {},
    availableFilters: MutableMap<String, Boolean> = mutableMapOf(),
    onFilterChange: (String, Boolean) -> Unit = { k, v -> availableFilters[k] = v },
    searchResultsStateList: SnapshotStateList<PKGSResult>,
    onSearchResultClick: (PKGSResult) -> Unit = { println("clicked $it") },
    selectedModule: String,
    addedModules: List<PKGSResult>
) {
    Column(Modifier) {
        SearchRow(textSearchState, onTextValueChange, onFilterChange, availableFilters)
        Spacer(Modifier.height(8.dp))
        val selectableState = rememberSelectableLazyListState()
        SelectableLazyColumn(state = selectableState) {
            stickyHeader("header") {
                Row(
                    Modifier.fillMaxWidth()
                        .background(LocalDisabledAreaColors.current.endBorderColor).fillMaxWidth()
                ) {
                    Label(
                        modifier = Modifier.padding(vertical = 4.dp),
                        text = "Added in $selectedModule (${addedModules.size})",
                        style = LocalDefaultTextStyle.current.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            addedModules.forEach {
                val key = it.hashCode()
                item(key) {
                    Row(
                        Modifier
                            .background(
                                if (isSelected) LocalFocusAreaColors.current.focusColor.copy(alpha = .3f) else Color.Unspecified
                            )
                    ) { it.composable(onSearchResultClick) }
                }
            }
            if (searchResultsStateList.isNotEmpty()) {
                item("spacer", selectable = false) {
                    Spacer(Modifier.height(8.dp))
                }
                stickyHeader("header2") {
                    Row(Modifier.fillMaxWidth().background(LocalDisabledAreaColors.current.endBorderColor).fillMaxWidth()) {
                        Label(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = "Search Results (${searchResultsStateList.size})",
                            style = LocalDefaultTextStyle.current.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                items(
                    count = searchResultsStateList.size,
                    key = {
                        it.hashCode()
                    },
                    contentType = {
                        it
                    }
                ) {
                    Row(
                        Modifier
                            .background(
                                if (isSelected) LocalFocusAreaColors.current.focusColor.copy(alpha = .3f) else Color.Unspecified
                            )
                    ) { searchResultsStateList[it].composable(onSearchResultClick) }
                }
            }
        }
    }
}

@Composable
fun SearchRow(
    textSearchState: String,
    onTextValueChange: (String) -> Unit,
    onFilterChange: (String, Boolean) -> Unit,
    availableFilters: MutableMap<String, Boolean>
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // text search
        TextField(
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth().weight(1f),
            value = textSearchState,
            onValueChange = onTextValueChange,
            leadingIcon = { Icon("icons/search.svg", colorFilter = ColorFilter.tint(LocalAreaColors.current.text.copy(alpha = .5f))) },
            trailingIcon = {
                if (textSearchState.isNotEmpty()) Icon(resource = "icons/closeSmall.svg", modifier = Modifier.clickable { onTextValueChange("") })
            }
        )
        // filters
        Row {
            if (availableFilters.isNotEmpty()) {
                availableFilters.forEach { filter ->
                    Row(Modifier.padding(start = 4.dp)) {
                        Checkbox(filter.value, { onFilterChange(filter.key, it) })
                        Label(modifier = Modifier.padding(start = 4.dp), text = filter.key)
                    }
                }
            }
        }
    }
}

data class PKGSResult(
    val name: String,
    val versions: List<String>,
    val targets: List<Target>,
    val scopes: List<String>,
    val action: String?,
    val stable: Boolean = true
) {

    @Composable
    fun composable(onSearchResultClick: (PKGSResult) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onSearchResultClick(this) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Label(text = name)
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.End) {
                targets.forEach {
                    it.composable()
                }
            }
            Row(horizontalArrangement = Arrangement.End) {
                val selectedScope = remember { mutableStateOf(scopes.first()) }
                DropdownSelector(selectedScope, scopes, modifier = Modifier.defaultMinSize(minWidth = 160.dp))
                val selectedVersion = remember { mutableStateOf(versions.first()) }
                DropdownSelector(selectedVersion, versions, modifier = Modifier.defaultMinSize(minWidth = 60.dp))
                Row(Modifier.defaultMinSize(60.dp, 0.dp).padding(start = 8.dp)) {
                    action?.let { action ->
                        Link(
                            text = action,
                            onClick = { println("clicked action: $action for $name") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownSelector(selected: MutableState<String>, pool: List<String>, modifier: Modifier) {
    val neutralLinkColors =
        LinkColors(
            LocalAreaColors.current,
            LocalAreaColors.current,
            LocalAreaColors.current,
            LocalAreaColors.current,
            LocalAreaColors.current,
            LocalAreaColors.current
        )
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        var menuOpen by remember { mutableStateOf(false) }
        DropdownLink(selected.value, { menuOpen = true }, colors = neutralLinkColors)
        DropdownMenu(modifier = Modifier.then(modifier).width(IntrinsicSize.Max), expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            pool.filter { it != selected.value }.forEach { item ->
                DropdownMenuItem({ selected.value = item; menuOpen = false }) {
                    Label(item)
                }
            }
        }
    }
}

fun pkgSearchResultGenerator(results: Int? = null): List<PKGSResult> {
    val numOfResults = results ?: Random.nextInt(60, 200)
    val pkgNameSet = setOf(
        "io.ktor:ktor-server-core-xyz",
        "org.apache.commons",
        "com.fasterxml.jackson.module",
        "joda-time",
        "org.jboss.logging",
        "jakarta.xml.bind-api-test",
        "com.fasterxml.jackson.core"
    )
    val targetsList = listOf(
        Target.ANDROID,
        Target.APPLE,
        Target.WATCHARM32,
        Target.IOSX64,
        Target.IOSARM64,
        Target.JS,
        Target.COMMON,
        Target.JVM
    )
    val targets = {
        buildList {
            repeat(Random.nextInt(1, 10)) {
                add(targetsList.random())
            }
        }
    }
    val scopes = listOf("Implementation", "TestImplementation")
    val version = {
        buildList {
            repeat(Random.nextInt(1, 8)) {
                add("${Random.nextInt(0, 3)}.${Random.nextInt(0, 12)}.${Random.nextInt(0, 12)}")
            }
        }.sorted()
    }
    return buildList {
        repeat(numOfResults) {
            add(
                PKGSResult(
                    name = pkgNameSet.random(),
                    versions = version(),
                    action = if (Random.nextBoolean()) "Upgrade" else null,
                    scopes = scopes.shuffled(),
                    targets = targets()
                )
            )
        }
    }
}
