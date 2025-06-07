package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

public interface ComponentStyling {
    public fun provide(vararg values: ProvidedValue<*>): ComponentStyling {
        if (values.isEmpty()) return this
        return with(StaticComponentStyling(values = values))
    }

    public fun provide(provider: @Composable () -> Array<out ProvidedValue<*>>): ComponentStyling =
        with(LazyComponentStyling(provider))

    public fun with(styling: ComponentStyling): ComponentStyling {
        if (styling is Companion) return this
        return CombinedComponentStyling(this, styling)
    }

    public fun with(styling: @Composable () -> ComponentStyling): ComponentStyling =
        with(LazyComponentStyling { styling().styles() })

    @Composable public fun styles(): Array<out ProvidedValue<*>>

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
