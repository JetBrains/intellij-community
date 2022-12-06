package org.jetbrains.jewel.util

import androidx.compose.foundation.lazy.LazyListState

val LazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size
