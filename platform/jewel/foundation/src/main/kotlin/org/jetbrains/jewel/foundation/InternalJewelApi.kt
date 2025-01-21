package org.jetbrains.jewel.foundation

import kotlin.RequiresOptIn.Level

@RequiresOptIn(
    level = Level.WARNING,
    message = "This is an internal API for Jewel and is subject to change without notice.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class InternalJewelApi
