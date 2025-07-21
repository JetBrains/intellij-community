// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

import com.intellij.java.JavaBundle

public class ExternalAnnotationLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  JavaBundle.message("line.marker.type.external.annotations"), LineMarkerType.External)

public class InferredNullabilityAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  JavaBundle.message("line.marker.type.inferred.nullability.annotations"), LineMarkerType.InferredNullability)

public class InferredContractAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  JavaBundle.message("line.marker.type.inferred.contract.annotations"), LineMarkerType.InferredContract) {
  override fun isEnabledByDefault(): Boolean = false
}
