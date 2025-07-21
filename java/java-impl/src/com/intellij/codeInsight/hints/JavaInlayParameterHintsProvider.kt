// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.completion.CompletionMemory
import com.intellij.codeInsight.completion.JavaMethodCallElement
import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*

public class JavaInlayParameterHintsProvider : InlayParameterHintsProvider {

  public object Utils {
    @JvmStatic
    public fun getInstance(): JavaInlayParameterHintsProvider = InlayParameterHintsExtension.forLanguage(JavaLanguage.INSTANCE) as JavaInlayParameterHintsProvider
  }
  
  override fun getHintInfo(element: PsiElement): MethodInfo? {
    if (element is PsiCallExpression && element !is PsiEnumConstant) {
      val resolvedElement = (if(JavaMethodCallElement.isCompletionMode(element)) CompletionMemory.getChosenMethod(element) else null)
                            ?: element.resolveMethodGenerics().element
      if (resolvedElement is PsiMethod) {
        return getMethodInfo(resolvedElement)
      }
    }
    return null
  }

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    if (element is PsiCall) {
      if (element is PsiEnumConstant && !isShowHintsForEnumConstants.get()) return emptyList()
      if (element is PsiNewExpression && !isShowHintsForNewExpressions.get()) return emptyList()
      return JavaInlayHintsProvider.hints(element).toList()
    }
    return emptyList()
  }

  override fun canShowHintsWhenDisabled(): Boolean {
    return true
  }

  private fun getMethodInfo(method: PsiMethod): MethodInfo? {
    val containingClass = method.containingClass ?: return null
    val fullMethodName = StringUtil.getQualifiedName(containingClass.qualifiedName, method.name)

    val paramNames: List<String> = method.parameterList.parameters.map { it.name }
    return MethodInfo(fullMethodName, paramNames)
  }

  override fun getDefaultBlackList(): Set<String> = defaultBlackList

  private val defaultBlackList = setOf(
      "(begin*, end*)",
      "(start*, end*)",
      "(first*, last*)",
      "(first*, second*)",
      "(from*, to*)",
      "(min*, max*)",
      "(key, value)",
      "(format, arg*)",
      "(message)",
      "(message, error)",
      
      "*Exception",

      "*.set*(*)",
      "*.add(*)",
      "*.set(*,*)",
      "*.get(*)",
      "*.create(*)",
      "*.getProperty(*)",
      "*.setProperty(*,*)",
      "*.print(*)",
      "*.println(*)",
      "*.append(*)",
      "*.charAt(*)",
      "*.indexOf(*)",
      "*.contains(*)",
      "*.startsWith(*)",
      "*.endsWith(*)",
      "*.equals(*)",
      "*.equal(*)",
      "*.compareTo(*)",
      "*.compare(*,*)",

      "java.lang.Math.*",
      "org.slf4j.Logger.*",
      
      "*.singleton(*)",
      "*.singletonList(*)",
      
      "*.Set.of",
      "*.ImmutableList.of",
      "*.ImmutableMultiset.of",
      "*.ImmutableSortedMultiset.of",
      "*.ImmutableSortedSet.of",
      "*.Arrays.asList"
  )
  
  public val showIfMethodNameContainsParameterName: Option = Option("java.method.name.contains.parameter.name",
                                                                    JavaBundle.messagePointer(
                                                               "settings.inlay.java.parameters.with.names.that.are.contained.in.the.method.name"),
                                                                    false)
  
  public val showForParamsWithSameType: Option = Option("java.multiple.params.same.type",
                                                        JavaBundle.messagePointer(
                                                   "settings.inlay.java.non.literals.in.case.of.multiple.parameters.with.the.same.type"),
                                                        false)
  
  public val showForBuilderLikeMethods: Option = Option("java.build.like.method",
                                                        JavaBundle.messagePointer("settings.inlay.java.builder.like.methods"),
                                                        false)


  public val ignoreOneCharOneDigitHints: Option = Option("java.simple.sequentially.numbered",
                                                         JavaBundle.messagePointer(
                                                    "settings.inlay.java.methods.with.same.named.numbered.parameters"),
                                                         false)

  public val isShowHintWhenExpressionTypeIsClear: Option = Option("java.clear.expression.type",
                                                                  JavaBundle.messagePointer(
                                                             "settings.inlay.java.complex.expressions.binary.functional.array.access.and.other"),
                                                                  false).also {
    it.extendedDescriptionSupplier = JavaBundle.messagePointer(
      "settings.inlay.java.show.parameter.hints.when.expression.type.is.clear.description")
  }

  public val isShowHintsForEnumConstants: Option = Option("java.enums",
                                                          JavaBundle.messagePointer("settings.inlay.java.enum.constants"),
                                                          true)

  public val isShowHintsForNewExpressions: Option = Option("java.new.expr",
                                                           JavaBundle.messagePointer("settings.inlay.java.new.expressions"),
                                                           true)

  override fun getSupportedOptions(): List<Option> {
    return listOf(
      showIfMethodNameContainsParameterName,
      showForParamsWithSameType,
      ignoreOneCharOneDigitHints,
      isShowHintsForEnumConstants,
      isShowHintsForNewExpressions,
      isShowHintWhenExpressionTypeIsClear
    )
  }

  override fun getSettingsPreview(): String {
    return "class A {\n  native void foo(String name, boolean isChanged);\n  \n  void bar() {\n    foo(\"\", false);\n  }\n}"
  }

  override fun isExhaustive(): Boolean {
    return true
  }

  override fun getMainCheckboxText(): String {
    return JavaBundle.message("settings.inlay.java.show.parameter.hints.for")
  }

  override fun getProperty(key: String): String {
    return JavaBundle.message(key)
  }
}