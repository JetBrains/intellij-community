// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

data class UntaintedConfiguration(val taintedAnnotations: List<String?>,
                                  val unTaintedAnnotations: List<String?>,
                                  val firstAnnotation: String?,
                                  val methodClass: List<String?>,
                                  val methodNames: List<String?>,
                                  val fieldClass: List<String?>,
                                  val fieldNames: List<String?>,
                                  val processOuterMethodAsQualifierAndArguments: Boolean,
                                  val processInnerMethodAsQualifierAndArguments: Boolean,
                                  val skipClasses: List<String?>,
                                  val parameterOfPrivateMethodIsUntainted: Boolean,
                                  val privateOrFinalFieldSafe: Boolean = false,
                                  val depthInside: Int = 5,
                                  val depthOutsideMethods: Int = 0,
                                  val depthNestedMethods: Int = 1) {
  fun copy(): UntaintedConfiguration {
    return UntaintedConfiguration(
      taintedAnnotations = ArrayList(taintedAnnotations),
      unTaintedAnnotations = ArrayList(unTaintedAnnotations),
      firstAnnotation = firstAnnotation,
      methodClass = ArrayList(methodClass),
      methodNames = ArrayList(methodNames),
      fieldClass = ArrayList(fieldClass),
      fieldNames = ArrayList(fieldNames),
      processOuterMethodAsQualifierAndArguments = processOuterMethodAsQualifierAndArguments,
      processInnerMethodAsQualifierAndArguments = processInnerMethodAsQualifierAndArguments,
      skipClasses = ArrayList(skipClasses),
      parameterOfPrivateMethodIsUntainted = parameterOfPrivateMethodIsUntainted,
      privateOrFinalFieldSafe = privateOrFinalFieldSafe,
      depthInside = depthInside,
      depthOutsideMethods = depthOutsideMethods,
      depthNestedMethods = depthNestedMethods
    )
  }
}
