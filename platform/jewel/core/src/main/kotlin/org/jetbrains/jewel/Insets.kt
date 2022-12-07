package org.jetbrains.jewel

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Insets(
    @Stable
    val left: Dp,
    @Stable
    val top: Dp,
    @Stable
    val right: Dp,
    @Stable
    val bottom: Dp
) {

    constructor(all: Dp) : this(all, all, all, all)
    constructor(horizontal: Dp, vertical: Dp) : this(horizontal, vertical, horizontal, vertical)

    companion object {

        val Empty = Insets(0.dp)
    }
}

val InsetsVectorConverter = TwoWayConverter<Insets, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left.value, it.top.value, it.right.value, it.bottom.value) },
    convertFromVector = { Insets(it.v1.dp, it.v2.dp, it.v3.dp, it.v4.dp) }
)

@Composable
inline fun <S> Transition<S>.animateInsets(
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Insets> = { spring() },
    label: String = "InsetsAnimation",
    targetValueByState: @Composable (state: S) -> Insets
): State<Insets> =
    animateValue(InsetsVectorConverter, transitionSpec, label, targetValueByState)
