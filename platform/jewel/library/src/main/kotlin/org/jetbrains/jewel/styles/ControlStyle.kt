package org.jetbrains.jewel.styles

class ControlStyle<TAppearance, TState>(configure: ControlStyleBuilder<TAppearance, TState>.() -> Unit) {

    private val variations = ControlStyleBuilder<TAppearance, TState>().build(configure)

    fun appearance(state: TState, variation: Any? = null): TAppearance {
        val tag = variation ?: defaultVariationTag
        val states = variations[tag] ?: error("Variation '$variation' was not configured")
        return states[state] ?: error("State '$state' was not configured")
    }

    companion object {

        val defaultVariationTag = object {}
    }

    class ControlStyleBuilder<TAppearance, TState> {

        private val variations = mutableMapOf<Any, Map<TState, TAppearance>>()

        fun default(configure: ControlVariationBuilder<TAppearance, TState>.() -> Unit) {
            variation(defaultVariationTag, configure)
        }

        fun variation(tag: Any, configure: ControlVariationBuilder<TAppearance, TState>.() -> Unit) {
            require(!variations.containsKey(tag)) { "Variation '$tag' has already been registered" }
            variations[tag] = ControlVariationBuilder<TAppearance, TState>(tag).build(configure)
        }

        fun build(configure: ControlStyleBuilder<TAppearance, TState>.() -> Unit): Map<Any, Map<TState, TAppearance>> {
            configure()
            return variations
        }
    }

    class ControlVariationBuilder<TAppearance, TState>(val variation: Any?) {

        private val states = mutableMapOf<TState, TAppearance>()

        fun state(state: TState, appearance: TAppearance) {
            require(!states.containsKey(state)) { "State '$state' has already been registered for variation '$variation'" }
            states[state] = appearance
        }

        fun build(configure: ControlVariationBuilder<TAppearance, TState>.() -> Unit): Map<TState, TAppearance> {
            configure()
            return states
        }
    }
}
