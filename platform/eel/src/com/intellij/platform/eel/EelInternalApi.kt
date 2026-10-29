// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus

@Target(
  AnnotationTarget.ANNOTATION_CLASS,
  AnnotationTarget.CLASS,
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.FIELD,
  AnnotationTarget.FILE,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.SOURCE)
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.eel.CharMappingTest",
  "com.intellij.platform.eel.impl.local.DestroyAwareOutput",
  "com.intellij.platform.eel.provider.EelPathConversionsKt",
  "com.intellij.platform.eel.provider.utils.impl.CharMappersKt",
  "com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwareFileSystem",
  "com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwarePath",
)
// TODO Do we need this annotation when there is VisibleToClasses?
annotation class EelInternalApi