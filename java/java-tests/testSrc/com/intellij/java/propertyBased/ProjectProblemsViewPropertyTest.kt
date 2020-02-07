// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl
import com.intellij.codeInsight.daemon.problems.MemberUsageCollector
import com.intellij.codeInsight.daemon.problems.ui.ProjectProblemsView
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.EdtTestUtil.Companion.runInEdtAndWait
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ThrowableRunnable
import com.siyeh.ig.psiutils.TypeUtils
import one.util.streamex.StreamEx
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import java.util.concurrent.Executor
import kotlin.math.absoluteValue

@SkipSlowTestLocally
class ProjectProblemsViewPropertyTest : BaseUnivocityTest() {

  override fun setUp() {
    super.setUp()
    myProject.replaceService(ProjectProblemsView::class.java, ProblemsCollector(), testRootDisposable)
  }

  fun testAllFilesWithProblemsReported() {
    PropertyChecker.customized()
      .withIterationCount(1).checkScenarios {
        ImperativeCommand { env ->
          val psiFile = env.generateValue(psiJavaFiles(), null)
          val relatedFiles = findRelatedFiles(psiFile)
          if (relatedFiles.isEmpty()) return@ImperativeCommand
          val members: List<PsiMember> = collectMembers(psiFile)
          if (members.isEmpty()) return@ImperativeCommand
          val member = env.generateValue(Generator.sampledFrom(members), null)
          // super() calls not supported yet
          if (member is PsiMethod && member.isConstructor) return@ImperativeCommand

          val usages = findUsages(member)
          if (usages == null || usages.isEmpty()) return@ImperativeCommand
          env.logMessage("found ${usages.size} usages of member `${JavaDocUtil.getReferenceText(myProject, member)}` and its parents")

          val modifications = Modification.getPossibleModifications(member)
          for (modification in modifications) {
            MadTestingUtil.changeAndRevert(myProject) {
              val editor = FileEditorManager.getInstance(myProject)
                .openTextEditor(OpenFileDescriptor(myProject, psiFile.virtualFile), true)!!

              val membersToSearch = when (modification.searchAllMembers()) {
                true -> if (members.any { !isCheapToSearch(it) }) null else members
                else -> listOf(member)
              }
              if (membersToSearch == null) return@changeAndRevert

              assertEmpty(findFilesWithBrokenUsages(relatedFiles, membersToSearch))
              runInEdtAndWait(ThrowableRunnable { PsiDocumentManager.getInstance(myProject).commitAllDocuments() })
              CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
              assertEmpty(getFilesWithProblems(relatedFiles))

              env.logMessage("applying modification: [$modification]")
              WriteCommandAction.runWriteCommandAction(myProject) { modification.apply(myProject) }
              env.logMessage("modification applied")

              val expected = findFilesWithBrokenUsages(relatedFiles, membersToSearch)
              runInEdtAndWait(ThrowableRunnable { PsiDocumentManager.getInstance(myProject).commitAllDocuments() })
              CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
              val actual = getFilesWithProblems(relatedFiles)
              assertContainsElements(actual, expected)
            }
          }
        }
      }
  }

  private fun findRelatedFiles(psiFile: PsiFile): Set<VirtualFile> {
    val targetFile = psiFile.virtualFile
    if (psiFile !is PsiClassOwner) return emptySet()
    return psiFile.classes.asSequence()
      .flatMap { ReferencesSearch.search(it).findAll().asSequence() }
      .filter { !JavaResolveUtil.isInJavaDoc(it.element) }
      .mapNotNull { it.element.containingFile.virtualFile }
      .filterTo(mutableSetOf()) { it != targetFile }
  }

  private fun collectMembers(psiFile: PsiClassOwner) =
    SyntaxTraverser.psiTraverser(psiFile).mapNotNull { it as? PsiMember }.filter { !it.hasModifier(JvmModifier.PRIVATE) }

  private fun findUsages(node: PsiMember): List<PsiElement>? {
    if (!isCheapToSearch(node)) return null
    val members = PsiTreeUtil.collectParents(node, PsiMember::class.java, true) { false }
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
      val psiManager = PsiManager.getInstance(myProject)
      val collector = object : MemberUsageCollector(name, member.containingFile, usageExtractor) {
        override fun process(psiFile: PsiFile): Boolean {
          val actualFile = psiManager.findFile(psiFile.virtualFile) ?: return true
          return super.process(actualFile)
        }
      }
      PsiSearchHelper.getInstance(project).processAllFilesWithWord(name, scope, collector, true)
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

  private fun findFilesWithBrokenUsages(relatedFiles: Set<VirtualFile>, members: List<PsiMember>): Set<VirtualFile> {
    val psiManager = PsiManager.getInstance(myProject)
    val filesWithErrors = mutableSetOf<VirtualFile>()
    for (file in relatedFiles) {
      val psiFile = psiManager.findFile(file) ?: continue
      if (HighlightErrorsSearcher.hasErrors(psiFile, members)) filesWithErrors.add(file)
    }
    return filesWithErrors
  }

  private fun getFilesWithProblems(relatedFiles: Set<VirtualFile>): Set<VirtualFile> {
    val problemsView = ProjectProblemsView.SERVICE.getInstance(myProject)
    return relatedFiles.filterTo(mutableSetOf()) { problemsView.getProblems(it).isNotEmpty() }
  }

  private class HighlightErrorsSearcher(holder: HighlightInfoHolder) : HighlightVisitorImpl() {

    init {
      prepareToRunAsInspection(holder)
    }

    companion object {
      internal fun hasErrors(psiFile: PsiFile, members: List<PsiMember>): Boolean {
        var foundError = false
        val visitor = object : JavaRecursiveElementVisitor() {
          override fun visitElement(element: PsiElement) {
            super.visitElement(element)
            if (foundError) return
            val holder = object : HighlightInfoHolder(psiFile) {
              override fun add(info: HighlightInfo?): Boolean {
                if (info == null || foundError || info.severity != HighlightSeverity.ERROR) return true
                val startElement = psiFile.findElementAt(info.actualStartOffset) ?: return true
                val endElement = psiFile.findElementAt(info.actualEndOffset - 1) ?: return true
                val reported = PsiTreeUtil.findCommonParent(startElement, endElement) ?: return true
                val context = PsiTreeUtil.getNonStrictParentOfType(reported,
                                                                   PsiStatement::class.java, PsiClass::class.java,
                                                                   PsiMethod::class.java, PsiField::class.java,
                                                                   PsiReferenceList::class.java) ?: return true

                foundError = StreamEx.ofTree(context, { StreamEx.of(*it.children) })
                  .anyMatch { el -> el is PsiReference && members.any { m -> el.isReferenceTo(m) } }
                return true
              }

              override fun hasErrorResults() = foundError
            }
            val searcher = HighlightErrorsSearcher(holder)
            element.accept(searcher)
          }
        }
        psiFile.accept(visitor)
        return foundError
      }
    }
  }

  private sealed class Modification(protected val member: PsiMember) {

    abstract fun apply(project: Project)

    abstract override fun toString(): String

    open fun searchAllMembers(): Boolean = false

    companion object {

      val classModifications = arrayOf(::ChangeName, ::MakePublic, ::MakePackagePrivate,
                                       ::InvertAbstract, ::InvertFinal,
                                       ::ChangeExtendsList, ::MakeClassInterface)

      val methodModifications = arrayOf(::ChangeName, ::MakePublic, ::MakeProtected, ::MakePrivate, ::MakePackagePrivate,
                                        ::ChangeReturnType, ::InvertAbstract, ::InvertFinal, ::InvertStatic,
                                        ::ChangeParamType, ::AddParam)

      val fieldModifications = arrayOf(::ChangeName, ::MakePublic, ::MakeProtected, ::MakePrivate, ::MakePackagePrivate,
                                       ::ChangeType, ::InvertFinal, ::InvertStatic)

      fun getPossibleModifications(member: PsiMember) = when (member) {
        is PsiClass -> classModifications.map { it(member) }
        is PsiMethod -> methodModifications.map { it(member) }
        is PsiField -> if (member is PsiEnumConstant) emptyList() else fieldModifications.map { it(member) }
        else -> emptyList()
      }
    }

    private class ChangeName(member: PsiMember) : Modification(member) {

      private val newName: String

      init {
        val namedMember = member as PsiNameIdentifierOwner
        val identifier = namedMember.nameIdentifier!!
        val oldName = identifier.text
        newName = oldName + oldName.hashCode().absoluteValue
      }

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        (member as PsiNameIdentifierOwner).nameIdentifier!!.replace(factory.createIdentifier(newName))
      }

      override fun toString(): String = "ChangeName: new name is '$newName'"
    }

    private abstract class ExplicitModifierModification(member: PsiMember,
                                                        private val modifier: JvmModifier,
                                                        private val modifierName: String) : Modification(member) {
      override fun apply(project: Project) {
        if (member.hasModifier(modifier)) return
        member.modifierList?.setModifierProperty(modifierName, true)
      }

      override fun toString(): String = "ExplicitModifierModification: access modifier is set to '$modifierName'"
    }

    private class MakePublic(member: PsiMember) : ExplicitModifierModification(member, JvmModifier.PUBLIC, PsiModifier.PUBLIC)
    private class MakeProtected(member: PsiMember) : ExplicitModifierModification(member, JvmModifier.PROTECTED, PsiModifier.PROTECTED)
    private class MakePrivate(member: PsiMember) : ExplicitModifierModification(member, JvmModifier.PRIVATE, PsiModifier.PRIVATE)

    private class MakePackagePrivate(member: PsiMember) : Modification(member) {

      override fun apply(project: Project) {
        val currentModifier = when {
          member.hasModifier(JvmModifier.PUBLIC) -> PsiModifier.PUBLIC
          member.hasModifier(JvmModifier.PROTECTED) -> PsiModifier.PROTECTED
          member.hasModifier(JvmModifier.PRIVATE) -> PsiModifier.PRIVATE
          else -> null
        }
        if (currentModifier == null) return
        member.modifierList?.setModifierProperty(currentModifier, false)
      }

      override fun toString(): String = "MakePackagePrivate: access modifier is set to 'package private'"
    }

    private class ChangeReturnType(method: PsiMethod) : Modification(method) {

      private val newType = if (method.returnType == PsiPrimitiveType.INT) TypeUtils.getStringType(method) else PsiPrimitiveType.INT

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        (member as PsiMethod).returnTypeElement!!.replace(factory.createTypeElement(newType))
      }

      override fun toString(): String =
        "ChangeReturnType: return type is changed to '$newType'"
    }

    private class ChangeType(field: PsiField) : Modification(field) {
      private val newType = if (field.type == PsiPrimitiveType.INT) TypeUtils.getStringType(field) else PsiPrimitiveType.INT

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        (member as PsiField).typeElement!!.replace(factory.createTypeElement(newType))
      }

      override fun toString(): String = "ChangeType: type is changed to '$newType'"
    }

    private open class InvertModifier(member: PsiMember,
                                      private val modifier: JvmModifier,
                                      private val modifierName: String) : Modification(member) {

      override fun apply(project: Project) {
        val hasModifier = member.hasModifier(modifier)
        member.modifierList!!.setModifierProperty(modifierName, !hasModifier)
      }

      override fun toString(): String = "InvertModifier: modifier '$modifierName' is inverted"
    }

    private class InvertFinal(member: PsiMember) : InvertModifier(member, JvmModifier.FINAL, PsiModifier.FINAL)
    private class InvertAbstract(member: PsiMember) : InvertModifier(member, JvmModifier.ABSTRACT, PsiModifier.ABSTRACT)
    private class InvertStatic(member: PsiMember) : InvertModifier(member, JvmModifier.STATIC, PsiModifier.STATIC)

    private class ChangeParamType(method: PsiMethod) : Modification(method) {

      val newType: PsiType?

      init {
        val params = method.parameterList.parameters
        newType = when {
          params.isEmpty() -> null
          else -> if (params[0].type == PsiPrimitiveType.INT) TypeUtils.getStringType(method) else PsiPrimitiveType.INT
        }
      }

      override fun apply(project: Project) {
        val param = (member as PsiMethod).parameterList.getParameter(0)
        if (param == null || newType == null) return
        val factory = JavaPsiFacade.getElementFactory(project)
        param.typeElement!!.replace(factory.createTypeElement(newType))
      }

      override fun toString() =
        if (newType == null) "ChangeParamType: no params, nothing to change"
        else "ChangeParamType: 1st parameter type is changed to '$newType'"
    }

    private class AddParam(method: PsiMethod) : Modification(method) {

      val paramName: String

      init {
        val methodName = method.name
        paramName = methodName + methodName.hashCode().absoluteValue
      }

      override fun apply(project: Project) {
        val factory = JavaPsiFacade.getElementFactory(project)
        val param = factory.createParameter(paramName, PsiType.INT)
        (member as PsiMethod).parameterList.add(param)
      }

      override fun toString(): String = "AddParam: param '$paramName' added"
    }

    private class ChangeExtendsList(psiClass: PsiClass) : Modification(psiClass) {

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

    private class MakeClassInterface(psiClass: PsiClass) : Modification(psiClass) {
      override fun apply(project: Project) {
        val psiClass = member as PsiClass
        if (psiClass.isEnum || psiClass.isInterface) return
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

  private class ProblemsCollector : ProjectProblemsView {

    private val problems: MutableMap<VirtualFile, MutableMap<Navigatable, String>> = mutableMapOf()

    override fun addProblem(file: VirtualFile, message: String, place: Navigatable) {
      problems.compute(file) { _, v -> v?.apply { this[place] = message } ?: mutableMapOf(place to message) }
    }

    override fun removeProblems(file: VirtualFile, place: Navigatable?) {
      if (place != null) problems.computeIfPresent(file) { _, v -> v.apply { this.remove(place) } }
      else problems.remove(file)
    }

    override fun getProblems(file: VirtualFile) = problems[file]?.keys?.toList() ?: emptyList()

    override fun init(toolWindow: ToolWindow) {}

    override fun executor() = Executor { ApplicationManager.getApplication().invokeAndWait(it) }
  }
}