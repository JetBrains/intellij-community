// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.createAddFieldActions
import com.intellij.lang.jvm.actions.expectedTypes
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.siyeh.HardcodedMethodConstants
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.fixes.SerialVersionUIDBuilder
import com.siyeh.ig.psiutils.SerializationUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.sourcePsiElement

class SerializableHasSerialVersionUidFieldInspection : USerializableInspectionBase() {
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
    val fixes = arrayOf(AddSerialVersionUidFix())
    return arrayOf(manager.createProblemDescriptor(identifier, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
  }

  inner class AddSerialVersionUidFix : LocalQuickFix {
    override fun getFamilyName(): String = InspectionGadgetsBundle.message("add.serialversionuidfield.quickfix")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val uClass = descriptor.psiElement.getUastParentOfType<UClass>() ?: return
      val psiClass = uClass.javaPsi
      val containingFile = psiClass.containingFile ?: return
      val uFactory = uClass.getUastElementFactory(project) ?: return
      val serialUid = SerialVersionUIDBuilder.computeDefaultSUID(psiClass)
      val initializer = uFactory.createLongConstantExpression(serialUid, null)?.sourcePsi ?: return
      val action = createAddFieldActions(psiClass,
        SerialVersionUIDFieldInfo(HardcodedMethodConstants.SERIAL_VERSION_UID, initializer, project)).firstOrNull() ?: return
      val editor = (FileEditorManager.getInstance(project).getSelectedEditor(containingFile.virtualFile) as? TextEditor)?.editor ?: return
      action.invoke(project, editor, containingFile)
    }
  }

  private class SerialVersionUIDFieldInfo(private val name: String,
                                          private val initializer: PsiElement,
                                          private val project: Project) : CreateFieldRequest {
    override fun getTargetSubstitutor(): JvmSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)

    override fun getInitializer(): PsiElement = initializer

    override fun getModifiers(): Collection<JvmModifier> = listOf(JvmModifier.PRIVATE, JvmModifier.STATIC)

    override fun isConstant(): Boolean = true

    override fun getFieldType(): List<ExpectedType> = expectedTypes(PsiType.LONG)

    override fun getFieldName(): String = name

    override fun isValid(): Boolean = true
  }
}