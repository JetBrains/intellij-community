// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

class ExternalAnnotationLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  CodeInsightBundle.message("line.marker.type.external.annotations"), LineMarkerType.External)

class InferredNullabilityAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  CodeInsightBundle.message("line.marker.type.inferred.nullability.annotations"), LineMarkerType.InferredNullability)

class InferredContractAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider(
  CodeInsightBundle.message("line.marker.type.inferred.contract.annotations"), LineMarkerType.InferredContract) {
  override fun isEnabledByDefault() = false
}
