package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.traverseDescendants
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.actionSystem.DataProviderNode

@VisibleForTesting
@ApiStatus.Internal
@InternalJewelApi
public class RootDataProviderNode : Modifier.Node(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
        val context = DataProviderDataSinkContext(sink)

        traverseDescendants(DataProviderNode) { dp ->
            if (dp is DataProviderNode) {
                if (!dp.hasFocus) {
                    return@traverseDescendants TraversableNode.Companion.TraverseDescendantsAction
                        .SkipSubtreeAndContinueTraversal
                } else {
                    dp.dataProvider(context)
                }
            }
            TraversableNode.Companion.TraverseDescendantsAction.ContinueTraversal
        }
    }
}
