package org.jetbrains.jewel.bridge

@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
)
annotation class SwingLafKey(
    val key: String,
)
