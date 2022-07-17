// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import org.jetbrains.annotations.ApiStatus

/*
 * Represent values that can be used lookup items reordering using machine learning models and to be safely collected to anonymous logs.
 *
 * ML Completion FAQ:
 * Q: I believe that <a fact about some items in lookup> might help to sort items better. What should I do?
 * A: It depends on a kind of the fact you want to leverage. If it's the same for all items, consider adding {@link ContextFeatureProvider}.
 * If the fact is about some items, add {@link ElementFeatureProvider}.
 *
 * Q: I want to add one more {@link ContextFeatureProvider} or {@link ElementFeatureProvider}. When the items ordering be finally changed?
 * A: Once newer ranking model is trained. Adding a new feature provider doesn't affect current ordering. But your score will be collected
 * during the nearest EAP and your fact may be leveraged to make resulting ordering better.
 *
 * Q: My computations do the same work for every single element inside the same session. Can I avoid that?
 * A: Yes. Consider using {@link ContextFeatureProvider}. Results of {@link ContextFeatureProvider} may be reused in inside
 * {@link ElementFeatureProvider} pre-computing is required one can use {@link ContextFeatureProvider} to place relatively computation and pass it using
 * user data of {@link CompletionEnvironment}
 *
 * Q: I noticed weird case when items inside lookup are ordered in a non-sense way. Can we fix that?
 * A: Probably, yes. If the case if quite frequent we can describe both items (relevant and non-relevant) to distinguish when while ranking.
 * If non-relevant item is chosen less frequently then it will have much less score than the relevant one.
 *
 * Q: How can I see what feature-values are used by ml ranking procedure?
 * A: There's "Copy ML Completion Features To Clipboard" action (default shortcut - ctrl shift alt 0). It saves element-wise features
 * values to the clipboard.
 */
@ApiStatus.Internal
sealed class MLFeatureValue {
  companion object {
    private val TRUE = BinaryValue(true)
    private val FALSE = BinaryValue(false)

    @JvmStatic
    fun binary(value: Boolean): MLFeatureValue = if (value) TRUE else FALSE

    @JvmStatic
    fun float(value: Int): MLFeatureValue = FloatValue(value.toDouble())

    @JvmStatic
    fun float(value: Double): MLFeatureValue = FloatValue(value)

    // alias for float(Int), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value: Int): MLFeatureValue = float(value)

    // alias for float(Double), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value: Double): MLFeatureValue = float(value)

    @JvmStatic
    fun <T : Enum<*>> categorical(value: T): MLFeatureValue = CategoricalValue(value.toString())

    @JvmStatic
    fun <T : Class<*>> className(value: T, useSimpleName: Boolean = true): MLFeatureValue = ClassNameValue(value, useSimpleName)

    @JvmStatic
    fun version(value: String): MLFeatureValue = VersionValue(value)
  }

  abstract val value: Any

  data class BinaryValue internal constructor(override val value: Boolean) : MLFeatureValue()
  data class FloatValue internal constructor(override val value: Double) : MLFeatureValue()
  data class CategoricalValue internal constructor(override val value: String) : MLFeatureValue()
  data class ClassNameValue internal constructor(override val value: Class<*>, val useSimpleName: Boolean) : MLFeatureValue()
  data class VersionValue internal constructor(override val value: String) : MLFeatureValue()
}
