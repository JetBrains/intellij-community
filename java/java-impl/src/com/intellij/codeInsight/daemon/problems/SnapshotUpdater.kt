// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

internal typealias Snapshot = Map<SmartPsiElementPointer<PsiMember>, ScopedMember>

internal data class ChangeSet(val newSnapshot: Snapshot, val changes: Map<PsiMember, ScopedMember?>)

internal class SnapshotUpdater(project: Project, private val prevSnapshot: Snapshot) : JavaElementVisitor() {

  private val pointerManager = SmartPointerManager.getInstance(project)

  private val snapshot = mutableMapOf<SmartPsiElementPointer<PsiMember>, ScopedMember>()
  private val changes = mutableMapOf<PsiMember, ScopedMember?>()

  override fun visitEnumConstant(psiEnumConstant: PsiEnumConstant) {
    val member = ScopedMember.create(psiEnumConstant) ?: return
    val (psiMember, prevMember) = visitMember(member, psiEnumConstant) ?: return
    changes[psiMember] = prevMember
  }

  override fun visitClass(psiClass: PsiClass) {
    val member = ScopedMember.create(psiClass) ?: return
    val (psiMember, prevMember) = visitMember(member, psiClass) ?: return
    changes[psiMember] = prevMember
    collectRelatedChanges(psiClass, member, prevMember, changes)
  }

  override fun visitField(psiField: PsiField) {
    val member = ScopedMember.create(psiField) ?: return
    val (psiMember, prevMember) = visitMember(member, psiField) ?: return
    changes[psiMember] = prevMember
  }

  override fun visitMethod(psiMethod: PsiMethod) {
    val member = ScopedMember.create(psiMethod) ?: return
    val (psiMember, prevMember) = visitMember(member, psiMethod) ?: return
    changes[psiMember] = prevMember
    collectRelatedChanges(psiMethod, member, prevMember, changes)
  }

  private fun visitMember(member: ScopedMember, psiMember: PsiMember): Pair<PsiMember, ScopedMember?>? {
    val pointer = pointerManager.createSmartPsiElementPointer(psiMember)
    snapshot[pointer] = member
    val prevMember = prevSnapshot[pointer]
    if (prevMember != null && !member.hasChanged(prevMember)) return null
    return psiMember to prevMember
  }

  companion object {
    private val OLD_CONTENT_KEY = Key.create<CharSequence>("OLD_CONTENT_KEY")
    private val SNAPSHOT_KEY = Key.create<Snapshot>("SNAPSHOT_KEY")

    @JvmStatic
    @JvmName("storeContent")
    internal fun storeContent(psiFile: PsiFile, content: CharSequence) {
      if (psiFile.getUserData(SNAPSHOT_KEY) != null || psiFile.getUserData(OLD_CONTENT_KEY) != null) return
      psiFile.putUserData(OLD_CONTENT_KEY, content)
    }

    @JvmStatic
    @JvmName("updateSnapshot")
    internal fun updateSnapshot(psiFile: PsiClassOwner, snapshot: Snapshot) {
      psiFile.putUserData(OLD_CONTENT_KEY, null)
      psiFile.putUserData(SNAPSHOT_KEY, snapshot)
    }

    @JvmStatic
    @JvmName("collectChanges")
    internal fun collectChanges(psiFile: PsiClassOwner): ChangeSet? {
      val snapshot = psiFile.getUserData(SNAPSHOT_KEY)
      if (snapshot != null) return update(psiFile, snapshot)
      val content = psiFile.getUserData(OLD_CONTENT_KEY)
      if (content != null) return update(psiFile, content)
      return null
    }

    private fun update(psiFile: PsiClassOwner, prevSnapshot: Snapshot): ChangeSet {
      val updater = SnapshotUpdater(psiFile.project, prevSnapshot)
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
      return ChangeSet(snapshot, changes)
    }

    private fun update(psiFile: PsiClassOwner, oldContent: CharSequence): ChangeSet? {
      val content = psiFile.viewProvider.contents
      if (content == oldContent) return null
      val project = psiFile.project
      val module = ModuleUtilCore.findModuleForFile(psiFile) ?: return null
      val scope = module.moduleScope
      val oldFile = parseFile(project, oldContent) ?: return null
      val publicMembers = constructMembers(psiFile) { !it.hasModifier(PsiModifier.PRIVATE) }
      val oldPsiMembers = publicApi(oldFile)
      val prevSnapshot: MutableMap<SmartPsiElementPointer<PsiMember>, ScopedMember> = mutableMapOf()
      val removedMembers = constructSnapshot(project, scope, publicMembers, oldPsiMembers, prevSnapshot)
      if (removedMembers.size == 1) {
        val removedMember = removedMembers[0]
        val replacement = findReplacement(removedMember, publicMembers, psiFile)
        if (replacement != null) prevSnapshot[SmartPointerManager.createPointer(replacement)] = removedMember
      }
      return update(psiFile, prevSnapshot)
    }

    private fun constructSnapshot(project: Project,
                                  scope: GlobalSearchScope,
                                  publicMembers: MutableMap<ScopedMember, PsiMember>,
                                  oldPsiMembers: List<PsiMember>,
                                  snapshot: MutableMap<SmartPsiElementPointer<PsiMember>, ScopedMember>): List<ScopedMember> {
      val pointerManager = SmartPointerManager.getInstance(project)
      val removedMembers = mutableListOf<ScopedMember>()
      for (oldPsiMember in oldPsiMembers) {
        val member = ScopedMember.create(oldPsiMember, scope) ?: continue
        val psiMember = publicMembers.remove(member)
        if (psiMember == null) {
          removedMembers.add(member)
          continue
        }
        val memberPointer = pointerManager.createSmartPsiElementPointer(psiMember)
        snapshot[memberPointer] = member
      }
      return removedMembers
    }

    private fun findReplacement(removedMember: ScopedMember,
                                publicMembers: MutableMap<ScopedMember,
                                  PsiMember>, psiFile: PsiClassOwner): PsiMember? {
      if (publicMembers.size == 1) return publicMembers.entries.first().value
      val removedPrivate = removedMember.asPrivate()
      val privateMembers = constructMembers(psiFile) { it.hasModifier(PsiModifier.PRIVATE) }
      return privateMembers[removedPrivate]
    }

    private fun parseFile(project: Project, content: CharSequence) =
      PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, content) as? PsiClassOwner

    private fun constructMembers(psiFile: PsiClassOwner, filter: (PsiMember) -> Boolean): MutableMap<ScopedMember, PsiMember> {
      val psiMembers = MemberCollector.collectMembers(psiFile, filter)
      val members = mutableMapOf<ScopedMember, PsiMember>()
      for (psiMember in psiMembers) {
        val member = ScopedMember.create(psiMember) ?: continue
        members[member] = psiMember
      }
      return members
    }

    private fun collectRelatedChanges(psiMember: PsiMember,
                                      curMember: ScopedMember,
                                      prevMember: ScopedMember?,
                                      changes: MutableMap<PsiMember, ScopedMember?>) {
      when (psiMember) {
        is PsiMethod -> {
          val containingClass = psiMember.containingClass ?: return
          // anonymous classes and lambdas creation might be broken, need to check class usages
          changes.putIfAbsent(containingClass, null)
        }
        is PsiClass -> {
          val prevClass = prevMember?.member as? Member.Class ?: return
          val curClass = curMember.member as? Member.Class ?: return
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