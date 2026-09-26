@Tag class Outer {
    fun outerOnly(): Int = 1
}

@Tag class Inner

fun outer(block: Outer.() -> Unit) { Outer().block() }

fun Outer.inner(block: Inner.() -> Unit) { Inner().block() }