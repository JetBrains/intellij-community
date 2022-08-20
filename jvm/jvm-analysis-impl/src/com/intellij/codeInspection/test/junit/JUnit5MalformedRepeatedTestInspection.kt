// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.findAnnotations
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnit5MalformedRepeatedTestInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return PsiElementVisitor.EMPTY_VISITOR
    val psiFacade = JavaPsiFacade.getInstance(holder.project)
    psiFacade.findClass(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPETITION_INFO, file.resolveScope) ?: return PsiElementVisitor.EMPTY_VISITOR
    psiFacade.findClass(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST, file.resolveScope) ?: return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(holder.file.language, Visitor(holder), arrayOf(UMethod::class.java), true)
  }
  
  private inner class Visitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitMethod(node: UMethod): Boolean {
      val sourcePsi = node.sourcePsi ?: return true
      val javaMethod = node.javaPsi
      val repeatedAnno = node.findAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST)
      if (repeatedAnno != null) {
        val testAnno = node.findAnnotations(
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_FACTORY
        )
        val toHighlight = testAnno.firstOrNull()?.sourcePsi
        if (testAnno.isNotEmpty() && toHighlight != null) {
          holder.registerProblem(toHighlight,
            JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.suspicious.combination"),
            DeleteElementFix(toHighlight)
          )
        }
        val repeatedNumber = repeatedAnno.findDeclaredAttributeValue("value")
        if (repeatedNumber != null) {
          val constant = repeatedNumber.evaluate()
          val repeatedSrcPsi = repeatedNumber.sourcePsi
          if (repeatedSrcPsi != null && constant is Int && constant <= 0) {
            holder.registerProblem(repeatedSrcPsi,
              JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.positive.number")
            )
          }
        }
      }
      else {
        val psiFacade = JavaPsiFacade.getInstance(holder.project)
        val repetitionInfo = psiFacade.findClass(
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPETITION_INFO, sourcePsi.resolveScope
        ) ?: return true
        val repetitionType = JavaPsiFacade.getElementFactory(holder.project).createType(repetitionInfo)
        val repetitionInfoParam = node.uastParameters.find { it.type == repetitionType }
        val paramAnchor = repetitionInfoParam?.uastAnchor?.sourcePsi
        if (repetitionInfoParam != null) {
          if (MetaAnnotationUtil.isMetaAnnotated(javaMethod, NON_REPEATED_ANNOTATIONS)) {
            holder.registerProblem(paramAnchor ?: repetitionInfoParam,
              JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.injected.for.repeatedtest")
            )
          }
          else {
            val anno = MetaAnnotationUtil.findMetaAnnotations(javaMethod, BEFORE_AFTER_ALL).findFirst().orElse(null)
            if (anno != null) {
              val qName = anno.qualifiedName
              holder.registerProblem(paramAnchor ?: repetitionInfoParam,
                JvmAnalysisBundle.message(
                  "jvm.inspections.junit5.malformed.repetition.description.injected.for.each", StringUtil.getShortName(qName!!))
              )
            }
            else {
              if (MetaAnnotationUtil.isMetaAnnotated(javaMethod, BEFORE_AFTER_EACH)
                  && javaMethod.containingClass?.methods?.find { MetaAnnotationUtil.isMetaAnnotated(it, NON_REPEATED_ANNOTATIONS) } != null
              ) { holder.registerProblem(paramAnchor ?: repetitionInfoParam,
                  JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.injected.for.test")
                )
              }
            }
          }
        }
      }
      return true
    }
  }

  companion object {
    val NON_REPEATED_ANNOTATIONS: List<String> = listOf(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_FACTORY,
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST
    )

    val BEFORE_AFTER_EACH = listOf(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_BEFORE_EACH, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_AFTER_EACH
    )

    val BEFORE_AFTER_ALL = listOf(
      JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_BEFORE_ALL, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_AFTER_ALL
    )
  }
}