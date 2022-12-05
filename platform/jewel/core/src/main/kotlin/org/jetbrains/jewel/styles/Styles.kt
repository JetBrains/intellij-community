package org.jetbrains.jewel.styles

import androidx.compose.runtime.compositionLocalOf
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

val LocalContentAlpha = compositionLocalOf { 1f }

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> localNotProvided(): T = error("CompositionLocal value for ${typeOf<T>().javaType} was not provided")

object Styles
