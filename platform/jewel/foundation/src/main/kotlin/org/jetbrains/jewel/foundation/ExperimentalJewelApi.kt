package org.jetbrains.jewel.foundation

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is an experimental API for Jewel and is likely to change before becoming " +
        "stable.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
annotation class ExperimentalJewelApi
