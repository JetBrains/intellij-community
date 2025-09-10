package org.jetbrains.jewel.foundation

import org.jetbrains.annotations.ApiStatus

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
 * Code annotated as generated is not included in Metalava checks, as it is generated from the IntelliJ sources and we
 * do not control its API.
 *
 * For example, see `AllIconsKeys`.
 */
@ApiStatus.Internal
@InternalJewelApi
public annotation class GeneratedFromIntelliJSources
