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
/**
 * APIs annotated as internal Jewel API are not meant for usage in client code; there are no guarantees about the binary
 * nor source compatibility, and the behavior can change at any time. **Do not use** these APIs in client code!
 *
 * Must always be paired with [org.jetbrains.annotations.ApiStatus.Internal].
 */
public annotation class InternalJewelApi
