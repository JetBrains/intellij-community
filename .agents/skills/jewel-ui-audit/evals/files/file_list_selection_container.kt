/*
 * A settings configurable for a Jewel-based IntelliJ plugin. The tabs switch between an installed
 * JSON editor and a plugin catalog browser. The selected tab body is made selectable so users can
 * copy plugin names and descriptions. Reviewers: focus on selection scope and pointer behavior.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private enum class CatalogTab(val label: String) {
  CATALOG("Catalog"),
  INSTALLED("Installed"),
}

data class CatalogPlugin(val name: String, val description: String, val installed: Boolean)

@Composable
fun PluginCatalogConfigPanel(
  plugins: List<CatalogPlugin>,
  onInstall: (CatalogPlugin) -> Unit,
  onShowDetails: (CatalogPlugin) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf(CatalogTab.CATALOG) }
  val tabs =
    remember(selectedTab) {
      CatalogTab.entries.map { tab ->
        TabData.Default(
          selected = selectedTab == tab,
          content = { tabState -> SimpleTabContent(label = tab.label, state = tabState) },
          onClick = { selectedTab = tab },
        )
      }
    }

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    TabStrip(tabs = tabs, style = JewelTheme.defaultTabStyle, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.padding(top = 8.dp))

    // Makes the whole active tab body selectable so users can copy plugin names/descriptions.
    SelectionContainer(Modifier.fillMaxSize()) {
      when (selectedTab) {
        CatalogTab.CATALOG -> CatalogPlugins(plugins, onInstall, onShowDetails)
        CatalogTab.INSTALLED -> InstalledPluginJson("{\n  \"plugins\": []\n}")
      }
    }
  }
}

@Composable
private fun CatalogPlugins(plugins: List<CatalogPlugin>, onInstall: (CatalogPlugin) -> Unit, onShowDetails: (CatalogPlugin) -> Unit) {
  LazyColumn {
    items(plugins) { plugin ->
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(key = AllIconsKeys.Nodes.Plugin, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
          Text(plugin.name)
          Text(plugin.description)
        }
        DisableSelection {
          DefaultButton(onClick = { onInstall(plugin) }, enabled = !plugin.installed) {
            Text(if (plugin.installed) "Installed" else "Install")
          }
        }
        IconButton(onClick = { onShowDetails(plugin) }) {
          Icon(key = AllIconsKeys.General.LinkDropTriangle, contentDescription = "More")
        }
      }
      Divider()
    }
  }
}

@Composable
private fun InstalledPluginJson(json: String) {
  Text(json)
}
