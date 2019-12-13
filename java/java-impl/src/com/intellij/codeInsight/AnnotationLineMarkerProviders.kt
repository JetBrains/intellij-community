// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

class ExternalAnnotationLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider("External annotations", LineMarkerType.External)

class InferredNullabilityAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider("Inferred nullability annotations", LineMarkerType.InferredNullability)

class InferredContractAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider("Inferred contract annotations", LineMarkerType.InferredContract) {
  override fun isEnabledByDefault() = false
}
