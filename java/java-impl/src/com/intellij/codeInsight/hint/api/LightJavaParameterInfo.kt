// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api

import com.intellij.util.Range

/**
 * Stores information about available signatures for the current context. The context means here the place where `Signature Help`
 * action is invoked.
 * @see LightJavaParameterInfoHandler
 */
public class LightJavaParameterInfo(
  public val methodPresentationList: List<LightJavaSignaturePresentation>,
  public val currentSignatureIndex: Int?,
  public val currentParameterIndex: Int?,
)

/**
 * Stores information about a particular signature within available overloads.
 * @see LightJavaParameterInfo
 */
public class LightJavaSignaturePresentation(
  public val label: String,
  public val parameterRangeList: List<LightJavaParameterPresentation>,
  public val currentParameterIndex: Int?,
)

/**
 * Stores simplified information about parameter within signature.
 * @see LightJavaParameterPresentation
 */
public class LightJavaParameterPresentation(
  public val range: Range<Int>,
  public val documentation: String?,
)