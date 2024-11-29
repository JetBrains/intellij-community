package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey.NotSelectable
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey.Selectable

/** Interface defining the scope for building a selectable lazy list. */
public interface SelectableLazyListScope {
    /**
     * Represents an item in a selectable lazy list.
     *
     * @param key The unique identifier for the item.
     * @param contentType The type of content displayed in the item.
     * @param selectable Determines if the item is selectable. Default is `true`.
     * @param content The content of the item as a composable function.
     */
    public fun item(
        key: Any,
        contentType: Any? = null,
        selectable: Boolean = true,
        content: @Composable SelectableLazyItemScope.() -> Unit,
    )

    /**
     * Represents a list of items based on the provided parameters.
     *
     * @param count The number of items in the list.
     * @param key A function that generates a unique key for each item based on its index.
     * @param contentType A function that returns the content type of an item based on its index. Defaults to `null`.
     * @param selectable A function that determines if an item is selectable based on its index. Defaults to `true`.
     * @param itemContent The content of each individual item, specified as a composable function that takes the item's
     *   index as a parameter.
     */
    public fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any? = { null },
        selectable: (index: Int) -> Boolean = { true },
        itemContent: @Composable SelectableLazyItemScope.(index: Int) -> Unit,
    )

    /**
     * A method that enables sticky header behavior in a list or grid view.
     *
     * @param key The unique identifier for the sticky header.
     * @param contentType The type of content in the sticky header.
     * @param selectable Specifies whether the sticky header is selectable.
     * @param content The content to be displayed in the sticky header, provided as a composable function
     */
    public fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        selectable: Boolean = false,
        content: @Composable SelectableLazyItemScope.() -> Unit,
    )
}

internal class SelectableLazyListScopeContainer : SelectableLazyListScope {
    /**
     * Provides a set of keys that cannot be selected. Here we use an assumption that amount of selectable items >>
     * amount of non-selectable items. So, for optimization we will keep only this set.
     *
     * @see [isKeySelectable]
     */
    private val nonSelectableKeys = hashSetOf<Any>()

    // TODO: [performance] we can get rid of that map if indices won't be used at all in the API
    private val keyToIndex = hashMapOf<Any, Int>()

    private val keys = mutableListOf<SelectableLazyListKey>()
    private val entries = mutableListOf<Entry>()

    fun getEntries() = entries.toList()

    fun getKeys() = keys.toList()

    internal sealed interface Entry {
        data class Item(
            val key: Any,
            val contentType: Any?,
            val content: @Composable (SelectableLazyItemScope.() -> Unit),
        ) : Entry

        data class Items(
            val count: Int,
            val key: (index: Int) -> Any,
            val contentType: (index: Int) -> Any?,
            val itemContent: @Composable (SelectableLazyItemScope.(index: Int) -> Unit),
        ) : Entry

        data class StickyHeader(
            val key: Any,
            val contentType: Any?,
            val content: @Composable (SelectableLazyItemScope.() -> Unit),
        ) : Entry
    }

    internal fun getKeyIndex(key: Any): Int? = keyToIndex[key]

    internal fun isKeySelectable(key: Any): Boolean = key !in nonSelectableKeys

    override fun item(
        key: Any,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        keyToIndex[key] = keys.size
        keys.add(if (selectable) Selectable(key) else NotSelectable(key))
        if (!selectable) {
            nonSelectableKeys.add(key)
        }
        entries.add(Entry.Item(key, contentType, content))
    }

    override fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any?,
        selectable: (index: Int) -> Boolean,
        itemContent: @Composable (SelectableLazyItemScope.(index: Int) -> Unit),
    ) {
        // TODO: [performance] now the implementation requires O(count) operations but should be
        // done in ~ O(1)
        for (index in 0 until count) {
            val isSelectable = selectable(index)
            val currentKey = key(index)
            if (!isSelectable) {
                nonSelectableKeys.add(currentKey)
            }
            keyToIndex[currentKey] = keys.size
            keys.add(if (isSelectable) Selectable(currentKey) else NotSelectable(currentKey))
        }
        entries.add(Entry.Items(count, key, contentType, itemContent))
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(
        key: Any,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        keyToIndex[key] = keys.size
        keys.add(if (selectable) Selectable(key) else NotSelectable(key))
        if (!selectable) {
            nonSelectableKeys.add(key)
        }
        entries.add(Entry.StickyHeader(key, contentType, content))
    }
}

public fun <T : Any> SelectableLazyListScope.items(
    items: List<T>,
    key: (item: T) -> Any = { it },
    contentType: (item: T) -> Any? = { it },
    selectable: (item: T) -> Boolean = { true },
    itemContent: @Composable SelectableLazyItemScope.(item: T) -> Unit,
) {
    items(
        count = items.size,
        key = { key(items[it]) },
        contentType = { contentType(items[it]) },
        selectable = { selectable(items[it]) },
        itemContent = { itemContent(items[it]) },
    )
}

public fun <T : Any> SelectableLazyListScope.itemsIndexed(
    items: List<T>,
    key: (index: Int, item: T) -> Any = { _, item -> item },
    contentType: (index: Int, item: T) -> Any? = { _, item -> item },
    selectable: (index: Int, item: T) -> Boolean = { _, _ -> true },
    itemContent: @Composable SelectableLazyItemScope.(index: Int, item: T) -> Unit,
) {
    items(
        count = items.size,
        key = { key(it, items[it]) },
        contentType = { contentType(it, items[it]) },
        selectable = { selectable(it, items[it]) },
        itemContent = { itemContent(it, items[it]) },
    )
}

@Composable
public fun LazyItemScope.SelectableLazyItemScope(
    isSelected: Boolean = false,
    isActive: Boolean = false,
): SelectableLazyItemScope = SelectableLazyItemScopeDelegate(this, isSelected, isActive)

internal class SelectableLazyItemScopeDelegate(
    private val delegate: LazyItemScope,
    override val isSelected: Boolean,
    override val isActive: Boolean,
) : SelectableLazyItemScope, LazyItemScope by delegate
