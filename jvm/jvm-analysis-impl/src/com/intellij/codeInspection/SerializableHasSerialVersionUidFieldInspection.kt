// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmValue
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddFieldActions
import com.intellij.lang.jvm.actions.expectedTypes
import com.intellij.lang.jvm.actions.fieldRequest
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.siyeh.HardcodedMethodConstants
import com.siyeh.ig.fixes.SerialVersionUIDBuilder
import com.siyeh.ig.psiutils.SerializationUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.sourcePsiElement

class SerializableHasSerialVersionUidFieldInspection : USerializableInspectionBase(UClass::class.java) {
  override fun getID(): String = "serial"

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val psiClass = aClass.javaPsi
    if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum || psiClass.isRecord) return emptyArray()
    if (psiClass is PsiTypeParameter || psiClass is PsiEnumConstantInitializer) return emptyArray()
    if (ignoreAnonymousInnerClasses && psiClass is PsiAnonymousClass) return emptyArray()
    val serialVersionUIDField = psiClass.findFieldByName(HardcodedMethodConstants.SERIAL_VERSION_UID, false)
    if (serialVersionUIDField != null) return emptyArray()
    if (!SerializationUtils.isSerializable(psiClass)) return emptyArray()
    if (SerializationUtils.hasWriteReplace(psiClass)) return emptyArray()
    if (isIgnoredSubclass(psiClass)) return emptyArray()
    val identifier = aClass.uastAnchor.sourcePsiElement ?: return emptyArray()
    val message = JvmAnalysisBundle.message("jvm.inspections.serializable.class.without.serialversionuid.problem.descriptor")
    return arrayOf(manager.createProblemDescriptor(
      identifier,
      message,
      isOnTheFly,
      if (isOnTheFly) createFix(psiClass).toTypedArray() else null,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    ))
  }

  private fun createFix(psiClass: PsiClass): List<LocalQuickFix> {
    val serialUid = SerialVersionUIDBuilder.computeDefaultSUID(psiClass)

    val project = psiClass.project
    val annotations = if (PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_14)) {
      listOf(annotationRequest("java.io.Serial"))
    }
    else emptyList()

    val actions = createAddFieldActions(psiClass, fieldRequest(
      fieldName = HardcodedMethodConstants.SERIAL_VERSION_UID,
      annotations = annotations,
      modifiers = listOf(JvmModifier.PRIVATE, JvmModifier.STATIC),
      fieldType = expectedTypes(PsiTypes.longType()),
      targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY),
      initializer = JvmValue.createLongValue(serialUid),
      isConstant = true
    ))
    return IntentionWrapper.wrapToQuickFixes(actions, psiClass.containingFile)
  }
}