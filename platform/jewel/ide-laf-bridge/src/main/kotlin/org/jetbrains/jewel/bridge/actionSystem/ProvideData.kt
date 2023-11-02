package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

/**
 * Layout composable that create the bridge between [Modifier.provideData]
 * used inside [content] and [component]. So, IntelliJ's [DataManager] can
 * use [component] as [DataProvider].
 *
 * When IntelliJ requests [getData] from [component] Compose will traverse
 * [DataProviderNode] hierarchy and collect it according [DataProvider]
 * rules (see javadoc).
 */
// TODO: choose better naming?
@Suppress("unused", "FunctionName")
@Composable
public fun ComponentDataProviderBridge(
    component: JComponent,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rootDataProviderModifier = remember { RootDataProviderModifier() }

    Box(modifier = Modifier.then(rootDataProviderModifier).then(modifier)) {
        content()
    }

    DisposableEffect(component) {
        DataManager.registerDataProvider(component, rootDataProviderModifier.dataProvider)

        onDispose { DataManager.removeDataProvider(component) }
    }
}

/**
 * Configure component to provide data for IntelliJ Actions system.
 *
 * Use this modifier to provide context related data that can be used by
 * IntelliJ Actions functionality such as Search Everywhere, Action Popups
 * etc.
 *
 * Important note: modifiers order is important, so be careful with order
 * of [focusable] and [provideData] (see [FocusEventModifierNode]).
 *
 * @see DataProvider
 * @see DataContext
 * @see ComponentDataProviderBridge
 */
@Suppress("unused")
public fun Modifier.provideData(dataProvider: (dataId: String) -> Any?): Modifier =
    this then DataProviderElement(dataProvider)

@VisibleForTesting
internal class RootDataProviderModifier : ModifierNodeElement<DataProviderNode>() {

    private val rootNode = DataProviderNode { null }

    val dataProvider: (String) -> Any? = { rootNode.getData(it) }

    override fun create() = rootNode

    override fun update(node: DataProviderNode) {
        // do nothing
    }

    override fun hashCode(): Int = rootNode.hashCode()

    override fun equals(other: Any?) = other === this
}

private fun DataProviderNode.getData(dataId: String): Any? {
    val focusedNode = this.traverseDownToFocused() ?: return null
    return focusedNode.collectData(dataId)
}

private fun DataProviderNode.collectData(dataId: String): Any? {
    var currentNode: DataProviderNode? = this
    while (currentNode != null) {
        val data = currentNode.dataProvider(dataId)
        if (data != null) {
            return data
        }
        currentNode = currentNode.parent
    }

    return null
}

private fun DataProviderNode.traverseDownToFocused(): DataProviderNode? {
    for (child in children) {
        if (child.hasFocus) {
            return child.traverseDownToFocused()
        }
    }

    return this.takeIf { hasFocus }
}
