// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "This is an internal API and is subject to change without notice.",
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
 * APIs annotated as internal are not meant for usage in client code; there are no guarantees about the binary
 * nor source compatibility, and the behavior can change at any time. **Do not use** these APIs in client code!
 */
annotation class InternalIconsApi