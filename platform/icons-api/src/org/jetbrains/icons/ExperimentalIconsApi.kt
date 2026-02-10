// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "This is an experimental API and is likely to change before becoming stable.",
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
 * APIs annotated as experimental are subject to change at any time, with no binary nor source compatibility
 * guarantees. Behavior might change at any time.
 *
 * Using any API annotated as experimental in client code should be done with caution, and you will have to take care of
 * breakages in your code when usages are impacted by a change.
 */
annotation class ExperimentalIconsApi