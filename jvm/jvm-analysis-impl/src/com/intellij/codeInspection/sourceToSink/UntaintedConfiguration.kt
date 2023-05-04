// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

data class UntaintedConfiguration(val taintedAnnotations: List<String?>,
                                           val unTaintedAnnotations: List<String?>,
                                           val firstAnnotation: String?,
                                           val methodClass: List<String?>,
                                           val methodNames: List<String?>,
                                           val fieldClass: List<String?>,
                                           val fieldNames: List<String?>,
                                           val processMethodAsQualifierAndArguments: Boolean,
                                           val skipClasses: List<String?>) {
  fun copy(): UntaintedConfiguration {
    return UntaintedConfiguration(ArrayList(taintedAnnotations), ArrayList(
      unTaintedAnnotations),
                                  firstAnnotation,
                                  ArrayList(methodClass), ArrayList(methodNames),
                                  ArrayList(fieldClass), ArrayList(fieldNames),
                                  processMethodAsQualifierAndArguments,
                                  ArrayList(skipClasses))
  }
}
