package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.node.ModifierNodeElement
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.InternalJewelApi

@VisibleForTesting
@ApiStatus.Internal
@InternalJewelApi
public class RootDataProviderModifier : ModifierNodeElement<RootDataProviderNode>(), UiDataProvider {
    private val rootNode = RootDataProviderNode()

    override fun uiDataSnapshot(sink: DataSink) {
        rootNode.uiDataSnapshot(sink)
    }

    override fun create(): RootDataProviderNode = rootNode

    override fun update(node: RootDataProviderNode) {
        // do nothing
    }

    override fun hashCode(): Int = rootNode.hashCode()

    override fun equals(other: Any?): Boolean = other === this
}
