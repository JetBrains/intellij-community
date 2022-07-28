package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

internal fun Modifier.appendIf(condition: Boolean, transformer: Modifier.() -> Modifier): Modifier =
    if (!condition) this else transformer()

val LazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }
