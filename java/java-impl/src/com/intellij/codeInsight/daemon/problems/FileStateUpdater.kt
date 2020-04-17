// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

internal typealias Snapshot = Map<SmartPsiElementPointer<PsiMember>, ScopedMember>

internal data class FileState(val snapshot: Snapshot, val changes: Map<PsiMember, ScopedMember?>)

internal class FileStateUpdater(private val prevSnapshot: Snapshot?) : JavaElementVisitor() {

  private val snapshot = mutableMapOf<SmartPsiElementPointer<PsiMember>, ScopedMember>()
  private val changes = mutableMapOf<PsiMember, ScopedMember?>()

  override fun visitEnumConstant(psiEnumConstant: PsiEnumConstant) = visitMember(psiEnumConstant)

  override fun visitClass(psiClass: PsiClass) = visitMember(psiClass)

  override fun visitField(psiField: PsiField) = visitMember(psiField)

  override fun visitMethod(psiMethod: PsiMethod) = visitMember(psiMethod)

  private fun visitMember(psiMember: PsiMember) {
    val member = ScopedMember.create(psiMember) ?: return
    val pointer = SmartPointerManager.createPointer(psiMember)
    snapshot[pointer] = member
    if (prevSnapshot == null) return
    val prevMember = prevSnapshot[pointer]
    if (prevMember != null && !member.hasChanged(prevMember)) return
    changes[psiMember] = prevMember
    collectRelatedChanges(psiMember, member, prevMember, changes)
  }

  companion object {

    @JvmStatic
    @JvmName("getState")
    internal fun getState(psiFile: PsiFile): FileState? {
      if (DumbService.isDumb(psiFile.project)) return null
      val storedState = FileStateCache.SERVICE.getInstance(psiFile.project).getState(psiFile)
      if (storedState != null) return storedState
      val updater = FileStateUpdater(null)
      publicApi(psiFile).forEach { it.accept(updater) }
      val snapshot = updater.snapshot
      return FileState(snapshot, emptyMap())
    }

    @JvmStatic
    @JvmName("findState")
    internal fun findState(psiFile: PsiFile, prevSnapshot: Snapshot): FileState {
      val updater = FileStateUpdater(prevSnapshot)
      publicApi(psiFile).forEach { it.accept(updater) }
      val snapshot = updater.snapshot
      val changes = updater.changes
      for ((memberPointer, prevMember) in prevSnapshot) {
        if (memberPointer in snapshot) continue
        val psiMember = memberPointer.element ?: continue
        val member = ScopedMember.create(psiMember) ?: continue
        changes[psiMember] = prevMember
        collectRelatedChanges(psiMember, member, prevMember, changes)
      }
      return FileState(snapshot, changes)
    }

    @JvmStatic
    @JvmName("setPreviousState")
    internal fun setPreviousState(psiFile: PsiFile) {
      val project = psiFile.project
      val fileStateCache = FileStateCache.SERVICE.getInstance(project)
      val (snapshot, changes) = fileStateCache.getState(psiFile) ?: return
      if (changes.isEmpty()) return
      val manager = SmartPointerManager.getInstance(project)
      val oldSnapshot = snapshot.toMutableMap()
      changes.forEach { (psiMember, prevMember) ->
        val memberPointer = manager.createSmartPsiElementPointer(psiMember)
        if (prevMember == null) oldSnapshot.remove(memberPointer)
        else oldSnapshot[memberPointer] = prevMember
      }
      fileStateCache.setState(psiFile, oldSnapshot, emptyMap())
    }

    @JvmStatic
    @JvmName("updateState")
    internal fun updateState(psiFile: PsiFile, fileState: FileState) {
      FileStateCache.SERVICE.getInstance(psiFile.project).setState(psiFile, fileState.snapshot, fileState.changes)
    }

    @JvmStatic
    @JvmName("removeState")
    internal fun removeState(psiFile: PsiFile) {
      FileStateCache.SERVICE.getInstance(psiFile.project).removeState(psiFile)
    }

    private fun collectRelatedChanges(
      psiMember: PsiMember,
      member: ScopedMember,
      prevMember: ScopedMember?,
      changes: MutableMap<PsiMember, ScopedMember?>
    ) {
      when (psiMember) {
        is PsiMethod -> {
          val containingClass = psiMember.containingClass ?: return
          // anonymous classes and lambdas creation might be broken, need to check class usages
          changes.putIfAbsent(containingClass, null)
        }
        is PsiClass -> {
          val prevClass = prevMember?.member as? Member.Class ?: return
          val curClass = member.member as? Member.Class ?: return
          when {
            prevClass.isInterface != psiMember.isInterface -> {
              // members usages might be broken, need to check them all
              publicApi(psiMember).forEach { changes.putIfAbsent(it, null) }
            }
            prevClass.extendsList != curClass.extendsList || prevClass.implementsList != curClass.implementsList -> {
              // maybe some parent members were referenced instead of current class overrides
              publicApi(psiMember).filter { it is PsiMethod && it.isOverride() }.forEach { changes.putIfAbsent(it, null) }
            }
          }
        }
      }
    }

    private fun publicApi(psiElement: PsiElement) = MemberCollector.collectMembers(psiElement) { !it.hasModifier(PsiModifier.PRIVATE) }

    private fun PsiMethod.isOverride() = hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)

    private fun PsiMember.hasModifier(modifier: String) = modifierList?.hasModifierProperty(modifier) ?: false
  }

}