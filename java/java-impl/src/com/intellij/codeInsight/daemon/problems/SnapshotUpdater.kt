// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.JBIterable

internal typealias Snapshot = MutableMap<SmartPsiElementPointer<PsiMember>, Member>

internal class SnapshotUpdater(private val prevSnapshot: Snapshot?,
                               private val containingFile: PsiFile,
                               private val containingClass: PsiClass) : JavaElementVisitor() {

  private val snapshot: Snapshot = mutableMapOf()
  private val changes: MutableSet<Change> = mutableSetOf()

  override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
    visitMember(enumConstant, Companion::hasChanged)?.apply { changes.add(this) }
  }

  override fun visitClass(psiClass: PsiClass) {
    val change = visitMember(psiClass, Companion::hasChanged) ?: return
    changes.add(change)
    val prevClass = change.prevMember?.psiMember as? PsiClass ?: return
    val curClass = change.curMember?.psiMember as? PsiClass ?: return
    if (prevClass.isInterface == curClass.isInterface) return
    // members usages might be broken, need to check them all
    val memberChanges = api(curClass).mapNotNull { createChange(it, containingFile) }
    changes.addAll(memberChanges)
  }

  override fun visitField(psiField: PsiField) {
    visitMember(psiField, Companion::hasChanged)?.apply { changes.add(this) }
  }

  override fun visitMethod(psiMethod: PsiMethod) {
    val change = visitMember(psiMethod, Companion::hasChanged)
    if (change != null) {
      // anonymous classes and lambdas creation might be broken, need to check class usages
      val classChange = createChange(containingClass, containingFile)
      if (classChange != null) changes.add(classChange)
      changes.add(change)
    }
  }

  private inline fun <reified T : PsiMember> visitMember(psiMember: T, hasChanged: (T, T) -> Boolean): Change? {
    val scope = psiMember.useScope as? GlobalSearchScope ?: return null
    val memberPointer: SmartPsiElementPointer<PsiMember> = SmartPointerManager.createPointer(psiMember)
    val prevMember = prevSnapshot?.remove(memberPointer)
    val prevPsiMember = prevMember?.psiMember as? T
    if (prevPsiMember == null || scope != prevMember.scope || hasChanged(prevPsiMember, psiMember)) {
      val copy = psiMember.copy() as? T ?: return null
      val curMember = Member.create(copy, scope) ?: return null
      snapshot[memberPointer] = curMember
      return Change(prevMember, curMember, containingFile)
    }
    snapshot[memberPointer] = prevMember
    return null
  }

  companion object {

    private val SNAPSHOT_KEY = Key.create<Snapshot>("SNAPSHOT_KEY")

    internal fun update(psiClass: PsiClass): Set<Change> {
      val prevSnapshot = psiClass.getUserData(SNAPSHOT_KEY)
      val containingFile = psiClass.containingFile
      val updater = SnapshotUpdater(prevSnapshot, containingFile, psiClass)
      api(psiClass).forEach { it.accept(updater) }
      val changes = updater.changes
      prevSnapshot?.values?.forEach {
        val method = it.psiMember as? PsiMethod
        if (method != null) {
          val classChange = createChange(psiClass, containingFile)
          if (classChange != null) changes.add(classChange)
        }
        changes.add(Change(it, null, containingFile))
      }
      psiClass.putUserData(SNAPSHOT_KEY, updater.snapshot)
      return changes
    }

    private fun hasChanged(prevEnumConstant: PsiEnumConstant, curEnumConstant: PsiEnumConstant) =
      prevEnumConstant.name != curEnumConstant.name

    private fun hasChanged(prevField: PsiField, curField: PsiField) =
      hasChanged(prevField as PsiMember, curField as PsiMember) ||
      prevField.type != curField.type

    private fun hasChanged(prevClass: PsiClass, curClass: PsiClass) =
      prevClass.isEnum != curClass.isEnum ||
      prevClass.isInterface != curClass.isInterface ||
      hasChanged(prevClass as PsiMember, curClass as PsiMember) ||
      prevClass.hasModifier(PsiModifier.ABSTRACT) != curClass.hasModifier(PsiModifier.ABSTRACT) ||
      hasChanged(prevClass.extendsList, curClass.extendsList) ||
      // not all errors would be reported, since some methods might've disappeared, and we check only class usages
      hasChanged(prevClass.implementsList, curClass.implementsList)

    private fun hasChanged(prevMethod: PsiMethod, curMethod: PsiMethod) =
      hasChanged(prevMethod as PsiMember, curMethod as PsiMember) ||
      prevMethod.hasModifier(PsiModifier.ABSTRACT) != curMethod.hasModifier(PsiModifier.ABSTRACT) ||
      prevMethod.returnType != curMethod.returnType ||
      hasChanged(prevMethod.parameterList, curMethod.parameterList)

    private fun hasChanged(prevParamsList: PsiParameterList, curParamsList: PsiParameterList): Boolean {
      val prevParams = prevParamsList.parameters
      val curParams = curParamsList.parameters
      if (prevParams.size != curParams.size) return true
      for (i in prevParams.indices) {
        if (prevParams[i].type != curParams[i].type) {
          return true
        }
      }
      return false
    }

    private fun hasChanged(prevMember: PsiMember, curMember: PsiMember): Boolean {
      if (prevMember.name != curMember.name) return true
      val prevModifiers = prevMember.modifierList
      val curModifiers = curMember.modifierList
      if (prevModifiers == null || curModifiers == null) return prevModifiers != curModifiers
      return hasChanged(prevModifiers, curModifiers)
    }

    private fun hasChanged(prevModifiers: PsiModifierList, curModifiers: PsiModifierList) =
      prevModifiers.hasModifierProperty(PsiModifier.STATIC) != curModifiers.hasModifierProperty(PsiModifier.STATIC) ||
      prevModifiers.hasModifierProperty(PsiModifier.FINAL) != curModifiers.hasModifierProperty(PsiModifier.FINAL) ||
      prevModifiers.hasModifierProperty(PsiModifier.PROTECTED) != curModifiers.hasModifierProperty(PsiModifier.PROTECTED) ||
      prevModifiers.hasModifierProperty(PsiModifier.PUBLIC) != curModifiers.hasModifierProperty(PsiModifier.PUBLIC)

    private fun hasChanged(prevReferenceList: PsiReferenceList?, curReferenceList: PsiReferenceList?): Boolean {
      if ((prevReferenceList == null) != (curReferenceList == null)) return true
      if (prevReferenceList == null || curReferenceList == null) return false
      if (prevReferenceList.text != curReferenceList.text) return true
      val prevTypes = prevReferenceList.referencedTypes
      val curTypes = curReferenceList.referencedTypes
      if (prevTypes.size != curTypes.size) return true
      for (i in curTypes.indices) {
        val prevType = prevTypes[i]
        val curType = curTypes[i]
        if (prevType != curType) return true
      }
      return false
    }

    internal fun api(psiElement: PsiElement): JBIterable<PsiMember> =
      SyntaxTraverser.psiTraverser(psiElement).filter(PsiMember::class.java).filter { !it.hasModifier(PsiModifier.PRIVATE) }

    private fun PsiMember.hasModifier(modifier: String) = modifierList?.hasModifierProperty(modifier) ?: false

    private fun createChange(psiMember: PsiMember, containingFile: PsiFile): Change? {
      val scope = containingFile.useScope as? GlobalSearchScope ?: return null
      val classMember = Member.create(psiMember, scope) ?: return null
      return Change(null, classMember, containingFile)
    }
  }
}