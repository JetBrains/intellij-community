// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.problems.MemberCollector
import com.intellij.codeInsight.daemon.problems.MemberUsageCollector
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils
import com.intellij.codeInsight.hints.BlockInlayRenderer
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewManager
import com.intellij.util.ArrayUtilRt
import com.siyeh.ig.psiutils.TypeUtils
import junit.framework.TestCase
import one.util.streamex.StreamEx
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.math.absoluteValue
import kotlin.test.assertNotEquals

@SkipSlowTestLocally
class ProjectProblemsViewPropertyTest : BaseUnivocityTest() {

  fun testAllFilesWithMemberNameReported() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    PropertyChecker.customized()
      .withIterationCount(50)
      .checkScenarios { ImperativeCommand(this::doTestAllFilesWithMemberNameReported) }
  }

  private fun doTestAllFilesWithMemberNameReported(env: ImperativeCommand.Environment) {
    val changedFiles = mutableSetOf<VirtualFile>()

    MadTestingUtil.changeAndRevert(myProject) {
      val nFilesToChange = env.generateValue(Generator.integers(1, 3), "Files to change: %s")
      var i = 0
      while (i < nFilesToChange) {
        val fileToChange = env.generateValue(psiJavaFiles(), null)
        val relatedFiles = findRelatedFiles(fileToChange)
        if (relatedFiles.isEmpty()) continue

        val members = findMembers(fileToChange)
        if (members.isEmpty()) continue

        val editor = openEditor(fileToChange.virtualFile)
        rehighlight(fileToChange, editor)
        if (getFilesReportedByProblemSearch(editor, fileToChange).isNotEmpty()) continue

        env.logMessage("Selected file: ${fileToChange.name}")

        val actual = changeSelectedFile(env, members, fileToChange)
        if (actual == null) break

        changedFiles.add(fileToChange.virtualFile)

        val expected = findFilesWithProblems(relatedFiles, members)
        assertContainsElements(actual, expected)

        i++
      }
    }

    // check that all problems disappeared after revert
    val psiManager = PsiManager.getInstance(myProject)
    for (changedFile in changedFiles) {
      val psiFile = psiManager.findFile(changedFile)!!
      val editor = openEditor(changedFile)
      rehighlight(psiFile, editor)
      val reportedChanges = ProjectProblemPassUtils.getInlays(editor)
      for ((member, inlay) in reportedChanges) {
        if (inlay != null) {
          TestCase.fail("Problems are still reported even after the fix. " +
                        "File: ${changedFile.name}, " +
                        "Member: ${JavaDocUtil.getReferenceText(myProject, member)}, " +
                        "Reported problems: ${extractProblems(changedFile, inlay)}")
        }
      }
    }
  }

  private data class ScopedMember(val psiMember: PsiMember, var scope: SearchScope)

  private fun changeSelectedFile(env: ImperativeCommand.Environment,
                                 members: List<ScopedMember>,
                                 fileToChange: PsiJavaFile): Set<VirtualFile>? {
    val reportedFiles = mutableSetOf<VirtualFile>()
    val nChanges = env.generateValue(Generator.integers(1, 5), "Changes to make: %s")
    for (j in 0 until nChanges) {
      val editor = (FileEditorManager.getInstance(myProject).selectedEditor as TextEditor).editor
      val member = env.generateValue(Generator.sampledFrom(members), null)
      val psiMember = member.psiMember
      val prevScope = psiMember.useScope
      env.logMessage("Changing member: ${JavaDocUtil.getReferenceText(myProject, psiMember)}")
      val usages = findUsages(psiMember)
      if (usages == null || usages.isEmpty()) {
        env.logMessage("Member has no usages (or too many). Skipping.")
        continue
      }
      env.logMessage("Found ${usages.size} usages of member and its parents")

      val modifications = Modification.getPossibleModifications(psiMember, env)
      if (modifications.isEmpty()) {
        env.logMessage("Don't know how to modify this member, skipping it.")
        continue
      }
      val modification = env.generateValue(Generator.sampledFrom(modifications), "Applying modification: %s")

      val membersToSearch = getMembersToSearch(member, modification, members)
      if (membersToSearch == null) {
        env.logMessage("Too costly to analyse change, skipping")
        continue
      }

      rehighlight(fileToChange, editor)
      WriteCommandAction.runWriteCommandAction(myProject) { modification.apply(myProject) }
      env.logMessage("Modification applied")
      rehighlight(fileToChange, editor)
      if (!isCheapToSearch(psiMember)) {
        env.logMessage("Too costly to analyze element after change, skipping all iteration")
        return null
      }
      reportedFiles.addAll(getFilesReportedByProblemSearch(editor, fileToChange))
      val curScope = psiMember.useScope
      member.scope = prevScope.union(curScope)
    }
    return reportedFiles
  }

  private fun getMembersToSearch(member: ScopedMember, modification: Modification, members: List<ScopedMember>): List<ScopedMember>? {
    if (!modification.searchAllMembers()) return listOf(member)
    if (members.any { !isCheapToSearch(it.psiMember) }) return null
    return members
  }

  private fun rehighlight(psiFile: PsiFile, editor: Editor): List<HighlightInfo> {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments()
    return CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
  }

  private fun openEditor(virtualFile: VirtualFile) =
    FileEditorManager.getInstance(myProject).openTextEditor(OpenFileDescriptor(myProject, virtualFile), true)!!

  private fun findRelatedFiles(psiFile: PsiFile): Set<VirtualFile> {
    val targetFile = psiFile.virtualFile
    if (psiFile !is PsiClassOwner) return mutableSetOf()
    return psiFile.classes.asSequence()
      .flatMap { ReferencesSearch.search(it).findAll().asSequence() }
      .map { it.element }
      .filter { !JavaResolveUtil.isInJavaDoc(it) }
      .mapNotNull { it.containingFile }
      .distinctBy { it.virtualFile }
      .mapNotNullTo(mutableSetOf()) {
        val virtualFile = it.virtualFile
        if (virtualFile == targetFile || hasErrors(it)) null else virtualFile
      }
  }

  private fun findMembers(psiFile: PsiClassOwner): List<ScopedMember> {
    return MemberCollector.collectMembers(psiFile) { member ->
      if (member is PsiMethod && member.isConstructor) return@collectMembers false
      val modifiers = member.modifierList ?: return@collectMembers true
      return@collectMembers !modifiers.hasExplicitModifier(PsiModifier.PRIVATE)
    }.map { ScopedMember(it, it.useScope) }
  }

  private fun findUsages(target: PsiMember): List<PsiElement>? {
    if (!isCheapToSearch(target)) return null
    val members = PsiTreeUtil.collectParents(target, PsiMember::class.java, true) { false }
    val usages = mutableListOf<PsiElement>()
    for (member in members) {
      val name = member.name ?: return null
      val scope = member.useScope as? GlobalSearchScope ?: return null
      val usageExtractor: (PsiFile, Int) -> PsiElement? = lambda@{ psiFile, index ->
        val identifier = psiFile.findElementAt(index) as? PsiIdentifier ?: return@lambda null
        return@lambda when (val parent = identifier.parent) {
          is PsiReference -> if (parent.isReferenceTo(member)) parent else null
          is PsiMethod -> if (isOverride(parent, member)) parent else null
          else -> null
        }
      }
      val collector = MemberUsageCollector(name, myProject, usageExtractor)
      PsiSearchHelper.getInstance(myProject).processAllFilesWithWord(name, scope, collector, true)
      val memberUsages = collector.collectedUsages ?: return null
      usages.addAll(memberUsages)
    }
    return usages
  }

  private fun isCheapToSearch(member: PsiMember): Boolean {
    val name = member.name ?: return false
    val module = ModuleUtilCore.findModuleForPsiElement(member) ?: return false
    val scope = GlobalSearchScope.moduleScope(module)
    val memberFile = member.containingFile
    return PsiSearchHelper.getInstance(myProject).isCheapEnoughToSearch(name, scope, memberFile, null) != TOO_MANY_OCCURRENCES
  }

  private fun isOverride(possibleOverride: PsiMethod, target: PsiMember): Boolean {
    val targetMethod = target as? PsiMethod ?: return false
    val overrideClass = possibleOverride.containingClass ?: return false
    val targetClass = targetMethod.containingClass ?: return false
    if (!overrideClass.isInheritor(targetClass, true)) return false
    return possibleOverride == JavaOverridingMethodsSearcher.findOverridingMethod(overrideClass, target, targetClass)
  }

  private fun findFilesWithProblems(relatedFiles: Set<VirtualFile>, members: List<ScopedMember>): Set<VirtualFile> {
    val psiManager = PsiManager.getInstance(myProject)
    val filesWithErrors = mutableSetOf<VirtualFile>()
    for (file in relatedFiles) {
      val psiFile = psiManager.findFile(file) ?: continue
      if (hasErrors(psiFile, members)) filesWithErrors.add(file)
    }
    return filesWithErrors
  }

  private fun hasErrors(psiFile: PsiFile, members: List<ScopedMember>? = null): Boolean {
    val infos = rehighlight(psiFile, openEditor(psiFile.virtualFile))
    return infos.any { info ->
      if (info.severity != HighlightSeverity.ERROR) return@any false
      if (members == null) return@any true
      val startElement = psiFile.findElementAt(info.actualStartOffset) ?: return@any false
      val endElement = psiFile.findElementAt(info.actualEndOffset - 1) ?: return@any false
      val reported = PsiTreeUtil.findCommonParent(startElement, endElement) ?: return@any false
      if (JavaResolveUtil.isInJavaDoc(reported)) return@any false
      val context = PsiTreeUtil.getNonStrictParentOfType(reported,
                                                         PsiStatement::class.java, PsiClass::class.java,
                                                         PsiMethod::class.java, PsiField::class.java,
                                                         PsiReferenceList::class.java) ?: return@any false
      return@any StreamEx.ofTree(context, { StreamEx.of(*it.children) })
        .anyMatch { el -> el is PsiReference && members.any { m -> el.isReferenceTo(m.psiMember) && inScope(el.containingFile, m.scope) } }
    }
  }

  private fun inScope(psiFile: PsiFile, scope: SearchScope): Boolean = scope.contains(psiFile.virtualFile)

  private fun extractProblems(virtualFile: VirtualFile, inlay: Inlay<*>): String {
    data class Problem(val fileName: String, val offset: Int, val selectedElement: String,
                       val context: String?, val fileErrors: List<HighlightInfo>)

    fun getProblem(openedFile: VirtualFile, textEditor: TextEditor): Problem {
      val editor = textEditor.editor
      val psiFile = PsiManager.getInstance(myProject).findFile(openedFile)!!
      val offset = editor.caretModel.offset
      val selectedElement = psiFile.findElementAt(offset)!!
      val context = PsiTreeUtil.getParentOfType(selectedElement,
                                                PsiStatement::class.java, PsiExpression::class.java,
                                                PsiMethod::class.java, PsiClass::class.java)
      val fileErrors = rehighlight(psiFile, editor).filter { it.severity == HighlightSeverity.ERROR }
      return Problem(openedFile.name, offset, selectedElement.text, context?.text, fileErrors)
    }

    clickOnInlay(inlay)

    val textEditor = FileEditorManager.getInstance(myProject).selectedEditor as TextEditor
    val openedFile = textEditor.file!!
    if (openedFile != virtualFile) return getProblem(openedFile, textEditor).toString()

    val usageView = UsageViewManager.getInstance(myProject).selectedUsageView!!
    val problems = usageView.usages.map { usage ->
      usage.navigate(true)
      val editor = FileEditorManager.getInstance(myProject).selectedEditor as TextEditor
      val file = editor.file!!
      getProblem(file, editor)
    }
    return problems.joinToString { it.toString() }
  }

  private fun getFilesReportedByProblemSearch(editor: Editor, psiFile: PsiFile): Set<VirtualFile> {
    val reportedChanges: Map<PsiMember, Inlay<*>> = ProjectProblemPassUtils.getInlays(editor)
    val virtualFile = psiFile.virtualFile
    val filesWithProblems = mutableSetOf<VirtualFile>()
    for (inlay in reportedChanges.values) {
      var selectedEditor = FileEditorManager.getInstance(myProject).selectedEditor
      (selectedEditor as TextEditor).editor.caretModel.moveToOffset(0)
      clickOnInlay(inlay)
      selectedEditor = FileEditorManager.getInstance(myProject).selectedEditor
      val openedFile = selectedEditor!!.file!!
      if (openedFile != virtualFile) {
        filesWithProblems.add(openedFile)
        openEditor(virtualFile)
      }
      else if ((selectedEditor as TextEditor).editor.caretModel.offset == 0) {
        val usageView = UsageViewManager.getInstance(myProject).selectedUsageView!!
        for (usage in usageView.usages) {
          val usageFile = (usage as UsageInfo2UsageAdapter).usageInfo.virtualFile!!
          if (usageFile != virtualFile) filesWithProblems.add(usageFile)
        }
      }
    }
    return filesWithProblems
  }

  companion object {
    private fun clickOnInlay(inlay: Inlay<*>) {
      val click = MouseEvent(JPanel(), 0, 0, 0, 0, 0, 0, true, MouseEvent.BUTTON1)
      val point = Point(0, 0)
      val renderer = inlay.renderer as BlockInlayRenderer
      val constrainedPresentations = renderer.getConstrainedPresentations()
      val presentation = constrainedPresentations[0]
      val rootPresentation = presentation.root
      val settingsOnClickPresentation = (rootPresentation.content as SequencePresentation).presentations[1] as OnClickPresentation
      val problemsHoverPresentation = settingsOnClickPresentation.presentation as OnHoverPresentation
      problemsHoverPresentation.mouseMoved(click, point)
      val delegatePresentation = problemsHoverPresentation.presentation as DynamicDelegatePresentation
      val problemsClickPresentation = delegatePresentation.delegate as OnClickPresentation
      problemsClickPresentation.mouseClicked(click, point)
    }
  }

  private sealed class Modification(protected val member: PsiMember, env: ImperativeCommand.Environment) {

    abstract fun apply(project: Project)

    abstract override fun toString(): String

    open fun searchAllMembers(): Boolean = false

    companion object {

      val classModifications = arrayOf(::ChangeName, ::ExplicitModifierModification, ::ChangeExtendsList, ::MakeClassInterface)

      val methodModifications = arrayOf(::ChangeName, ::ExplicitModifierModification, ::ChangeType, ::AddParam)

      val fieldModifications = arrayOf(::ChangeName, ::ExplicitModifierModification, ::ChangeType)

      fun getPossibleModifications(member: PsiMember, env: ImperativeCommand.Environment) = when (member) {
        is PsiClass -> classModifications.map { it(member, env) }
        is PsiMethod -> methodModifications.map { it(member, env) }
        is PsiField -> if (member is PsiEnumConstant) emptyList() else fieldModifications.map { it(member, env) }
        else -> emptyList()
      }

      private fun String.absHash(): Int {
        val hash = hashCode()
        return if (hash == Int.MIN_VALUE) Int.MAX_VALUE else hash.absoluteValue
      }
    }

    private class ChangeName(member: PsiMember, env: ImperativeCommand.Environment) : Modification(member, env) {

      private val newName: String

      init {
        val namedMember = member as PsiNameIdentifierOwner
        val identifier = namedMember.nameIdentifier!!
        val oldName = identifier.text
        newName = oldName + oldName.absHash()
      }

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        (member as PsiNameIdentifierOwner).nameIdentifier!!.replace(factory.createIdentifier(newName))
      }

      override fun toString(): String = "ChangeName: new name is '$newName'"
    }

    private class ExplicitModifierModification(member: PsiMember, env: ImperativeCommand.Environment) : Modification(member, env) {

      private val modifier: String = env.generateValue(Generator.sampledFrom(*MODIFIERS), null)

      override fun apply(project: Project) {
        val modifiers = member.modifierList ?: return
        val hasModifier = modifiers.hasExplicitModifier(modifier)
        if (!hasModifier && isAccessModifier(modifier)) {
          val curAccessModifier = getAccessModifier(modifiers)
          if (curAccessModifier != null) modifiers.setModifierProperty(curAccessModifier, false)
        }
        modifiers.setModifierProperty(modifier, !hasModifier)
      }

      companion object {
        private val MODIFIERS = arrayOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED,
                                        PsiModifier.PRIVATE, PsiModifier.STATIC,
                                        PsiModifier.ABSTRACT, PsiModifier.FINAL)
        private val ACCESS_MODIFIERS = arrayOf(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED)

        private fun isAccessModifier(modifier: String) = modifier in ACCESS_MODIFIERS

        private fun getAccessModifier(modifiers: PsiModifierList): String? =
          sequenceOf(*ACCESS_MODIFIERS).firstOrNull { modifiers.hasExplicitModifier(it) }
      }

      override fun toString(): String = "ExplicitModifierModification: tweaking modifier '$modifier'"
    }

    private class ChangeType(member: PsiMember, env: ImperativeCommand.Environment) : Modification(member, env) {

      private val typeElement = env.generateValue(Generator.sampledFrom(findTypeElements(member)), null)
      private val newType = if (typeElement.type == PsiPrimitiveType.INT) TypeUtils.getStringType(typeElement) else PsiPrimitiveType.INT

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        typeElement.replace(factory.createTypeElement(newType))
      }

      private fun findTypeElements(member: PsiMember): List<PsiTypeElement> {
        return StreamEx.ofTree(member as PsiElement) { el ->
          if (el is PsiCodeBlock || el is PsiComment) StreamEx.empty()
          else StreamEx.of(*el.children)
        }.filterIsInstance<PsiTypeElement>()
      }

      override fun toString(): String =
        "ChangeType: '${typeElement.type.canonicalText}' is changed to '${newType.canonicalText}'"
    }

    private class AddParam(method: PsiMethod, env: ImperativeCommand.Environment) : Modification(method, env) {

      val paramName: String

      init {
        val methodName = method.name
        paramName = methodName + methodName.absHash()
      }

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        val param = factory.createParameter(paramName, PsiType.INT)
        (member as PsiMethod).parameterList.add(param)
      }

      override fun toString(): String = "AddParam: param '$paramName' added"
    }

    private class ChangeExtendsList(psiClass: PsiClass, env: ImperativeCommand.Environment) : Modification(psiClass, env) {

      private val parentRef: PsiElement?

      init {
        val refs = psiClass.extendsList?.referenceElements
        parentRef = if (refs == null || refs.size != 1) null else refs[0].element
      }

      override fun apply(project: Project) {
        if (parentRef == null) return
        val factory = JavaPsiFacade.getElementFactory(project)
        parentRef.replace(factory.createTypeElement(TypeUtils.getObjectType(parentRef)))
      }

      override fun searchAllMembers() = true

      override fun toString(): String =
        if (parentRef != null) "ChangeExtendsList: change '${parentRef.text}' to 'java.lang.Object'"
        else "ChangeExtendsList: class doesn't extend anything, do nothing"
    }

    private class MakeClassInterface(psiClass: PsiClass, env: ImperativeCommand.Environment) : Modification(psiClass, env) {
      override fun apply(project: Project) {
        val psiClass = member as PsiClass
        if (psiClass.isEnum || psiClass.isInterface || psiClass.isAnnotationType) return
        val classKeyword = PsiTreeUtil.getPrevSiblingOfType(psiClass.nameIdentifier!!, PsiKeyword::class.java) ?: return
        val factory = JavaPsiFacade.getElementFactory(project)
        val newTypeKeyword = factory.createKeyword(PsiKeyword.INTERFACE)
        PsiUtil.setModifierProperty(psiClass, PsiModifier.ABSTRACT, false)
        PsiUtil.setModifierProperty(psiClass, PsiModifier.FINAL, false)
        classKeyword.replace(newTypeKeyword)
      }

      override fun searchAllMembers() = true

      override fun toString(): String = "MakeClassInterface: type changed to 'interface'"
    }
  }
}