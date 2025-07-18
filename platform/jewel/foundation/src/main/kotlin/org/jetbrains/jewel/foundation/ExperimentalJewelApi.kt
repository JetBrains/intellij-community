package org.jetbrains.jewel.foundation

import kotlin.RequiresOptIn.Level

@RequiresOptIn(
    level = Level.WARNING,
    message = "This is an experimental API for Jewel and is likely to change before becoming stable.",
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
/**
 * APIs annotated as experimental Jewel API are subject to change at any time, with no binary nor source compatibility
 * guarantees. Behavior might change at any time.
 *
 * Using any API annotated as experimental in client code should be done with caution, and you will have to take care of
 * breakages in your code when usages are impacted by a change in a Jewel update.
 *
 * Must always be paired with [org.jetbrains.annotations.ApiStatus.Experimental].
 */
public annotation class ExperimentalJewelApi
