package org.jetbrains.jewel

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is an internal API for Jewel and is subject to change without notice.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
)
annotation class InternalJewelApi
