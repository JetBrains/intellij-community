package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.markdown.MarkdownBlock

/**
 * The parsed content of a YAML front matter metadata block.
 *
 * Front matter is rendered as a headerless two-column table where the first column contains the
 * [entries' keys][Entry.key] and the second column their [content][Entry.value]. Scalar values are rendered as
 * paragraphs, while list values are rendered as unordered lists, preserving the vertical structure of the block.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class FrontMatter(public val entries: List<Entry>) : MarkdownBlock.CustomBlock {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrontMatter

        if (entries != other.entries) return false

        return true
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "FrontMatter(entries=$entries)"

    /**
     * A single front matter entry.
     *
     * @param key The entry's key, rendered as plain text in the first column.
     * @param value The entry's value, rendered in the second column. A [Value.Scalar] for scalar values, or a
     *   [Value.ListValue] for list values.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Entry(public val key: String, public val value: Value) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry

            if (key != other.key) return false
            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        override fun toString(): String = "Entry(key='$key', value=$value)"
    }

    /** The value of a front matter [Entry]: either a single [Scalar] value or a [ListValue] holding multiple values. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public sealed interface Value {
        /**
         * A single scalar value — one atomic string. Rendered as a paragraph in the value column.
         *
         * @param text The value's text.
         */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Scalar(public val text: String) : Value {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Scalar

                if (text != other.text) return false

                return true
            }

            override fun hashCode(): Int = text.hashCode()

            override fun toString(): String = "Scalar(text='$text')"
        }

        /**
         * A list (YAML sequence) of values. Rendered as an unordered list in the value column.
         *
         * @param items The list's values, in order.
         */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class ListValue(public val items: List<String>) : Value {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ListValue

                if (items != other.items) return false

                return true
            }

            override fun hashCode(): Int = items.hashCode()

            override fun toString(): String = "ListValue(items=$items)"
        }
    }
}
