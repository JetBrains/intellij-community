package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Defines a composable styling contract that provides [ProvidedValue] entries for Jewel components, and can be combined
 * or layered with other instances.
 */
public interface ComponentStyling {
    /** Returns a new [ComponentStyling] that appends the given static [values] to this styling's provided values. */
    public fun provide(vararg values: ProvidedValue<*>): ComponentStyling {
        if (values.isEmpty()) return this
        return with(StaticComponentStyling(values = values))
    }

    /** Returns a new [ComponentStyling] that appends values produced lazily by [provider] at composition time. */
    public fun provide(provider: @Composable () -> Array<out ProvidedValue<*>>): ComponentStyling =
        with(LazyComponentStyling(provider))

    /** Returns a new [ComponentStyling] that combines this styling with [styling], applying [styling] last. */
    public fun with(styling: ComponentStyling): ComponentStyling {
        if (styling is Companion) return this
        return CombinedComponentStyling(this, styling)
    }

    /**
     * Returns a new [ComponentStyling] that combines this styling with the [ComponentStyling] produced lazily by
     * [styling] at composition time.
     */
    public fun with(styling: @Composable () -> ComponentStyling): ComponentStyling =
        with(LazyComponentStyling { styling().styles() })

    /** Returns the array of [ProvidedValue] entries that this styling contributes to the composition. */
    @Composable public fun styles(): Array<out ProvidedValue<*>>

    /** Companion object for [ComponentStyling]. */
    public companion object : ComponentStyling {
        override fun with(styling: ComponentStyling): ComponentStyling = styling

        @Composable override fun styles(): Array<out ProvidedValue<*>> = emptyArray()

        override fun toString(): String = "ComponentStyleProvider"
    }
}

private class StaticComponentStyling(private val values: Array<out ProvidedValue<*>>) : ComponentStyling {
    @Composable override fun styles(): Array<out ProvidedValue<*>> = values

    override fun equals(other: Any?): Boolean = other is StaticComponentStyling && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "StaticComponentStyle(values=${values.contentToString()})"
}

private class LazyComponentStyling(val provider: @Composable () -> Array<out ProvidedValue<*>>) : ComponentStyling {
    @Composable override fun styles(): Array<out ProvidedValue<*>> = provider()

    override fun equals(other: Any?): Boolean = other is LazyComponentStyling && provider == other.provider

    override fun hashCode(): Int = provider.hashCode()

    override fun toString(): String = "DynamicComponentStyleProvider(provider=$provider)"
}

private class CombinedComponentStyling(private val left: ComponentStyling, private val right: ComponentStyling) :
    ComponentStyling {
    @Composable
    override fun styles(): Array<out ProvidedValue<*>> =
        (left.styles().toList() + right.styles().toList()).toTypedArray()

    override fun equals(other: Any?): Boolean =
        other is CombinedComponentStyling && left == other.left && right == other.right

    override fun hashCode(): Int = left.hashCode() + 31 * right.hashCode()

    override fun toString(): String = "CombinedComponentStyleProvider(left=$left, right=$right)"
}
