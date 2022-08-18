// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

/**
 * Mapping between psi members and their properties model.
 * These properties are used to determine if member state has changed or not.
 */
internal typealias Snapshot = Map<SmartPsiElementPointer<PsiMember>, ScopedMember>

/**
 * Pair of snapshot and changes.
 * Changes contain mapping between psi members and their previous state.
 */
internal data class FileState(val snapshot: Snapshot, val changes: Map<PsiMember, ScopedMember?>)

internal class FileStateUpdater(private val prevState: FileState?) : JavaElementVisitor() {

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
    if (prevState == null) return
    val prevMember = prevState.snapshot[pointer]
    if (prevMember != null && !member.hasChanged(prevMember)) return
    changes[psiMember] = prevMember
    collectRelatedChanges(psiMember, member, prevMember, changes, prevState.changes)
  }

  companion object {

    /**
     * Extracts current file state from cache or creates a new one if it is not in cache.
     */
    @JvmStatic
    @JvmName("getState")
    internal fun getState(psiFile: PsiFile): FileState? {
      if (DumbService.isDumb(psiFile.project)) return null
      val storedState = FileStateCache.getInstance(psiFile.project).getState(psiFile)
      if (storedState != null) return storedState
      val updater = FileStateUpdater(null)
      publicApi(psiFile).forEach { it.accept(updater) }
      val snapshot = updater.snapshot
      return FileState(snapshot, emptyMap())
    }

    /**
     * Constructs new state based on a previous one
     * 
     * Construction contains three stages:
     * 1. analyze current state of psi file. for each member that already was in a snapshot check if it changed.
     * 2. put elements that were removed from psi file into set of changes
     * 3. add related changes to set of changes
     * (e.g. if additional method was added to functional interface then it cannot be used in lambdas and we have to check its usages)
     */
    @JvmStatic
    @JvmName("findState")
    internal fun findState(psiFile: PsiFile, prevSnapshot: Snapshot, prevChanges: Map<PsiMember, ScopedMember?>): FileState {
      val updater = FileStateUpdater(FileState(prevSnapshot, prevChanges))
      publicApi(psiFile).forEach { it.accept(updater) }
      val snapshot = updater.snapshot
      val changes = updater.changes
      for ((memberPointer, prevMember) in prevSnapshot) {
        if (memberPointer in snapshot) continue
        val psiMember = memberPointer.element ?: continue
        val member = ScopedMember.create(psiMember) ?: continue
        changes[psiMember] = prevMember
        collectRelatedChanges(psiMember, member, prevMember, changes, prevChanges)
      }
      return FileState(snapshot, changes)
    }

    /**
     * Restore file state based on changes in snapshot
     */
    @JvmStatic
    @JvmName("setPreviousState")
    internal fun setPreviousState(psiFile: PsiFile) {
      val project = psiFile.project
      val fileStateCache = FileStateCache.getInstance(project)
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
      FileStateCache.getInstance(psiFile.project).setState(psiFile, fileState.snapshot, fileState.changes)
    }

    @JvmStatic
    @JvmName("removeState")
    internal fun removeState(psiFile: PsiFile) {
      FileStateCache.getInstance(psiFile.project).removeState(psiFile)
    }

    private fun collectRelatedChanges(
      psiMember: PsiMember,
      member: ScopedMember,
      prevMember: ScopedMember?,
      changes: MutableMap<PsiMember, ScopedMember?>,
      prevChanges: Map<PsiMember, ScopedMember?>
    ) {
      // new member, maybe it is recreated
      val recreated = prevChanges.entries.find {
        val changedMember = it.value ?: return@find false
        return@find member::class == changedMember::class && member.name == changedMember.name
      }
      if (recreated != null) changes.putIfAbsent(recreated.key, recreated.value)

      when (psiMember) {
        is PsiMethod -> {
          val containingClass = psiMember.containingClass ?: return
          // anonymous classes and lambdas creation might be broken, need to check class usages
          changes.putIfAbsent(containingClass, prevChanges[containingClass])
        }
        is PsiClass -> {
          val prevClass = prevMember?.member as? Member.Class ?: return
          val curClass = member.member as? Member.Class ?: return
          when {
            prevClass.name != psiMember.name -> {
              // some reported problems might be fixed after such change, need to recheck them
              prevChanges.forEach { changes.putIfAbsent(it.key, it.value) }
            }
            prevClass.isInterface != psiMember.isInterface -> {
              // members usages might be broken, need to check them all
              publicApi(psiMember).forEach { changes.putIfAbsent(it, prevChanges[it]) }
            }
            prevClass.extendsList != curClass.extendsList || prevClass.implementsList != curClass.implementsList -> {
              // maybe some parent members were referenced instead of current class overrides
              // also maybe this overrides now reference something else in current class (e.g. private method)
              MemberCollector.collectMembers(psiMember) { it is PsiMethod }.forEach { changes.putIfAbsent(it, prevChanges[it]) }
            }
          }
        }
      }
    }

    private fun publicApi(psiElement: PsiElement) = MemberCollector.collectMembers(psiElement) { !it.hasModifier(PsiModifier.PRIVATE) }

    private fun PsiMember.hasModifier(modifier: String) = modifierList?.hasModifierProperty(modifier) ?: false
  }

}