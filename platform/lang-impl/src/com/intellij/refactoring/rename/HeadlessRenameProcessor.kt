// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.rename.RenameInputValidatorRegistry.getInputErrorValidator
import com.intellij.refactoring.rename.RenameUtil.isValidName
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<HeadlessRenameProcessor>()

/**
 * Renames a symbol for a caller that has no user to ask, such as an MCP tool or a language server.
 *
 * It never shows a dialog. Every question that [RenameProcessor] puts to the user becomes a default
 * answer or a refusal with a reason. It applies the whole rename, or it applies nothing and states
 * why. The one exception is a write that stops after every check passed, and
 * [HeadlessRenameFailure.WRITE_FAILED] reports that with what it wrote.
 *
 * Drive it with [analyze], then with [RenamePlan.apply]. Two phases let a caller read the blast
 * radius before a write, and let a language server take a snapshot of the files for a diff.
 *
 * Two deliberate differences from a rename in the IDE:
 * - Automatic renamers do not run unless the caller asks for them.
 * - A comment, a string and a plain-text occurrence are never searched.
 *
 * This adds no rename logic. [HeadlessRenameDriver] drives the platform engine, and replaces only
 * the phases that ask the user, so the platform rename engine stays the single engine.
 *
 * Every processor answers its own questions through [HeadlessRenamePsiElementProcessor]. The rename of
 * an element of a language that [HeadlessRenameSupportedLanguage] does not name is refused with
 * [HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED], and so is the rename of an element whose interactive
 * processor can ask the user and carries no headless registration. An element that the default
 * processor renames needs no registration, because that processor asks nothing.
 */
@ApiStatus.Experimental
object HeadlessRenameProcessor {
  /**
   * Resolves the target, then finds the usages and the conflicts of its rename to [newName].
   * Writes nothing.
   *
   * Returns [HeadlessRenameResult.Planned] when the rename can run. Call [RenamePlan.apply] to
   * write it.
   *
   * ### Before the call
   *
   * Commit the documents. This method does not, because a commit needs the write-intent lock, and
   * the thread here may hold a read action only.
   *
   * ### The thread
   *
   * Call this from a background thread, inside a read action. The Kotlin Analysis API refuses to
   * resolve on the dispatch thread, and a language server counts its own event thread as one.
   *
   * [HeadlessRenamePsiElementProcessor] states the same thread, so a processor of a language runs no
   * modal progress here. A modal progress needs the write thread to enter the modality, and it stops
   * the interface of a person who asked for nothing.
   *
   * @param readOnlyUsages what to do with a usage in a file that cannot be written
   * @param applyAutomaticRenamers true also renames what an [AutomaticRenamer] holds, such as a
   * variable named after a renamed class, or a parameter of an override. It takes the renamers of the
   * rename dialog, with each option of them answered from the settings of the IDE.
   */
  @RequiresReadLock
  @JvmStatic
  @JvmOverloads
  fun analyze(
    project: Project,
    target: PsiElement,
    newName: String,
    readOnlyUsages: ReadOnlyUsagePolicy = ReadOnlyUsagePolicy.REFUSE,
    applyAutomaticRenamers: Boolean = false,
  ): HeadlessRenameResult {
    if (!target.isValid) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, "The target element is no longer valid.")
    }
    isSupportedState(project, target)?.let { return it }

    val targetProcessors = when (val processorLookup = headlessProcessorsOf(target)) {
      is HeadlessProcessorLookup.Found -> processorLookup
      is HeadlessProcessorLookup.Missing ->
        return HeadlessRenameResult.Failed(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, processorLookup.detail())
    }
    // Turns an override into its super method, a constructor into its class, and an accessor into
    // its property, with every question of the language answered by a default.
    val element = when (val targetProcessor = targetProcessors.first) {
      // The default processor renames the target itself, as RenamePsiElementProcessorBase states.
      null -> target
      else -> targetProcessor.substituteElementToRenameHeadless(target)
              ?: return HeadlessRenameResult.Failed(HeadlessRenameFailure.TARGET_NOT_RENAMABLE,
                                                    "The language refused to substitute the target.")
    }
    if (element is PsiCompiledElement) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.TARGET_NOT_RENAMABLE,
                                         "The target is a compiled element. Change it upstream.")
    }
    val hasRenameProcessor = headlessProcessorsOf(element).let {
      it is HeadlessProcessorLookup.Found && it.processors.isNotEmpty()
    }
    PsiElementRenameHandler.getRenameErrorMessage(project, null, element, hasRenameProcessor)?.let {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, it)
    }

    var validationResult = getInputErrorValidator(element)?.`fun`(newName)
    if (validationResult == null && !isValidName(project, element, newName)) {
      validationResult = "'$newName' is not a valid name for this element."
    }

    validationResult?.let {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.NEW_NAME_REFUSED, it)
    }
    return HeadlessRenameDriver(project, element, newName, readOnlyUsages, applyAutomaticRenamers).plan()
  }

  /**
   * Reports whether [analyze] can plan a rename of [target], and asks the user nothing.
   *
   * It answers the question a client puts before it offers the rename, such as the LSP
   * `textDocument/prepareRename` request. A true answer is not a promise: [analyze] can still
   * refuse on a conflict or on a read-only file.
   *
   * It takes the same thread as [analyze].
   */
  @RequiresReadLock
  @JvmStatic
  fun canRename(target: PsiElement): Boolean {
    if (!target.isValid) return false
    if (isSupportedState(target.project, target) != null) return false
    val processorLookup = headlessProcessorsOf(target)
    if (processorLookup !is HeadlessProcessorLookup.Found) return false
    // The default processor renames the target itself, so the target answers for it.
    val targetProcessor = processorLookup.first ?: return target !is PsiCompiledElement
    return targetProcessor.substituteElementToRenameHeadless(target).let { it != null && it !is PsiCompiledElement }
  }
}

private fun isSupportedState(project: Project, element: PsiElement): HeadlessRenameResult.Failed? {
  if (DumbService.isDumb(project)) {
    return HeadlessRenameResult.Failed(HeadlessRenameFailure.INDEX_NOT_READY,
                                       "The indexes are not ready, so the usage set would be incomplete.")
  }
  if (!isHeadlessRenameSupported(element.language)) {
    return HeadlessRenameResult.Failed(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED,
                                       "The headless rename does not cover ${element.language.displayName} yet.")
  }
  return null
}

/**
 * Whether every rename processor of [language] carries a headless registration.
 * It is a temporary solution until other languages are not supported.
 */
private fun isHeadlessRenameSupported(language: Language): Boolean {
  if (language === Language.ANY) return true
  val supported = HeadlessRenameSupportedLanguage.EP_NAME.extensionList.mapTo(HashSet()) { it.language }
  var candidate: Language? = language
  while (candidate != null) {
    if (candidate.id in supported) return true
    candidate = candidate.baseLanguage
  }
  return false
}

/** The answer of [headlessProcessorsOf]. */
private sealed interface HeadlessProcessorLookup {
  /**
   * Every headless processor which renames the element, in the order of the extension point.
   *
   * The list is empty when an interactive rename takes the default processor. That one asks the user
   * nothing, so a headless rename needs no answer from it.
   */
  class Found(val processors: List<HeadlessRenamePsiElementProcessor>) : HeadlessProcessorLookup {
    /**
     * The one processor that answers a question which takes one answer, or null for the default answer.
     *
     * An interactive rename substitutes the element and finds the conflicts of the new name with one
     * processor, and it collects the other elements to rename with every one.
     * [PsiElementRenameHandler] takes `forPsiElement`, which is the first that claims the element,
     * and calls `substituteElementToRename` on that one alone. [RenameProcessor.preprocessUsages]
     * calls `findExistingNameConflicts` on `forElement`, which is the same one.
     */
    val first: HeadlessRenamePsiElementProcessor? get() = processors.firstOrNull()
  }

  /**
   * No headless processor claims the element, and the interactive one can ask the user.
   *
   * @param interactiveProcessor the processor which renames the element with a user. It needs a
   * [HeadlessRenamePsiElementProcessor] registration.
   */
  class Missing(private val interactiveProcessor: Class<*>) : HeadlessProcessorLookup {
    /** Says what a person who reads the refusal has to do about it. */
    fun detail(): String = "${interactiveProcessor.simpleName} renames this element, and it has no headless registration."
  }
}

/**
 * The processors of [element], or the report that a registration is missing.
 *
 * A headless rename renames with [HeadlessRenamePsiElementProcessor] alone. This reads the interactive
 * extension point for one more answer, and for no rename: it tells a language which needs a headless
 * registration from a language which needs none.
 *
 * The answer holds only where a product registers both points, which is every product with an
 * interactive rename. A product which registers the headless point alone, such as the language server,
 * reads `Found` for every element, and it states the languages it converted with
 * [HeadlessRenameSupportedLanguage] instead.
 */
private fun headlessProcessorsOf(element: PsiElement): HeadlessProcessorLookup {
  val processors = HeadlessRenamePsiElementProcessor.EP_NAME.extensionList
    .filter { it.canProcessElementHeadless(element) }
  if (processors.isNotEmpty()) return HeadlessProcessorLookup.Found(processors)
  // Nothing claims the element. The interactive extension point tells the two cases apart. The
  // default processor renames the element itself and asks nothing, so a headless rename runs it as
  // it stands. Any other processor holds a question that no registration answers yet.
  val interactiveProcessor = RenamePsiElementProcessorBase.forPsiElement(element)
  return if (interactiveProcessor is RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor) {
    HeadlessProcessorLookup.Found(emptyList())
  }
  else HeadlessProcessorLookup.Missing(interactiveProcessor.javaClass)
}

/**
 * Drives [RenameProcessor] with every question answered instead of asked.
 *
 * It stays a [RenameProcessor], because that is the platform rename engine. It repeats no method of
 * it. It overrides the phases that ask the user, and the platform runs every other step.
 */
internal class HeadlessRenameDriver(
  project: Project,
  private val primaryElement: PsiElement,
  private val newName: String,
  private val readOnlyUsages: ReadOnlyUsagePolicy,
  private val applyAutomaticRenamers: Boolean,
) : RenameProcessor(project, primaryElement, newName, false, false) {
  private val collectedConflicts = mutableListOf<HeadlessRenameConflict>()
  private val skippedFiles = mutableListOf<String>()
  private val collectedNotes = mutableListOf<String>()
  private var refusalMessage: String? = null
  private var writeRefusal: WriteRefusal? = null
  private var performed = false
  private var planApplied = false

  /**
   * The processors of [primaryElement], which is the element after the substitution.
   *
   * [HeadlessRenameProcessor.analyze] looks the processors of the target up, and this looks the
   * processors of the substituted element up. The two differ where the substitution crosses a kind of
   * element, as it does for a Java constructor. That one becomes its class.
   */
  private val processorLookup = headlessProcessorsOf(primaryElement)

  private val headlessProcessors: HeadlessProcessorLookup.Found?
    get() = processorLookup as? HeadlessProcessorLookup.Found

  /**
   * The processor which searches the usages of [element] and writes its new name.
   *
   * It takes the core of the processor of [HeadlessRenamePsiElementProcessor], so a caller with no user
   * registers on that extension point alone, and nothing here reads the interactive extension point.
   *
   * It takes [RenamePsiElementProcessorCore.DEFAULT] for an element that the default processor renames.
   * [headlessProcessorsOf] answers `Found` with an empty list for that element.
   */
  override fun processorFor(element: PsiElement): RenamePsiElementProcessorCore {
    val found = headlessProcessorsOf(element) as? HeadlessProcessorLookup.Found
    return found?.first?.processorCore() ?: RenamePsiElementProcessorCore.DEFAULT
  }

  /**
   * Plans the rename, and turns a known failure of any phase into a report.
   *
   * [RenameUtil.findUsages] runs three times here: once for the usages of the primary element, once
   * per element an automatic renamer adds, and once inside
   * [HeadlessRenamePsiElementProcessor.findExistingNameConflictsHeadless]. Each one resolves, so each
   * one can report that the indexes went away, or that a reference belongs to a language which cannot
   * take part. So the mapping wraps the whole body, and not one search.
   */
  fun plan(): HeadlessRenameResult {
    try {
      return planWithoutUser()
    }
    catch (_: UnknownReferenceTypeException) {
      // The language sits on the exception, but its getter is not visible outside the refactoring module.
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.UNSUPPORTED_REFERENCE_LANGUAGE, null)
    }
    catch (e: IndexNotReadyException) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.INDEX_NOT_READY, e.message)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error(e)
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.UNKNOWN, e.message)
    }
  }

  private fun planWithoutUser(): HeadlessRenameResult {
    if (processorLookup is HeadlessProcessorLookup.Missing) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, processorLookup.detail())
    }
    prepareRenamingWithoutUser(headlessProcessors?.processors.orEmpty(), primaryElement, newName, myAllRenames)

    val refUsages: Ref<Array<UsageInfo>> = Ref.create(findUsages())
    if (!preprocessUsages(refUsages)) {
      if (collectedConflicts.isNotEmpty()) return HeadlessRenameResult.Refused(collectedConflicts.toList())
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.NEW_NAME_REFUSED, refusalMessage)
    }

    var usages = refUsages.get()
    val descriptor = createUsageViewDescriptor(usages)

    if (computeUnloadedModulesFromUseScope(descriptor).isNotEmpty()) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.INCOMPLETE_USE_SCOPE, null)
    }

    val lockedDeclarations = readOnlyDeclarationFiles(descriptor)
    val lockedUsages = readOnlyUsageFiles(usages)
    val refusedFiles = when (readOnlyUsages) {
      ReadOnlyUsagePolicy.REFUSE -> (lockedDeclarations + lockedUsages).distinct()
      ReadOnlyUsagePolicy.SKIP -> lockedDeclarations
    }
    if (refusedFiles.isNotEmpty()) {
      return HeadlessRenameResult.Failed(
        HeadlessRenameFailure.READ_ONLY_USAGES,
        "These files hold a declaration or a usage to rename, and they cannot be written: " +
        refusedFiles.joinToString(", ") + ".")
    }
    if (lockedUsages.isNotEmpty()) {
      usages = usages.filter { it.element != null && it.isWritable }.toTypedArray()
      collectedNotes += "These files cannot be written, so their usages keep the old name: " +
                        lockedUsages.joinToString(", ") + "."
    }

    return HeadlessRenameResult.Planned(
      RenamePlan(this, usages, SmartPointerManager.createPointer(primaryElement),
                 affectedFiles(usages, descriptor), collectedNotes.toList(), psiModificationCount()))
  }

  /**
   * Collects the other elements to rename through [HeadlessRenamePsiElementProcessor], and asks the
   * user nothing.
   *
   * Every processor of the element answers, as in an interactive rename.
   * [RenameProcessor.prepareRenaming] calls each processor of `allForElement`, because two processors
   * of one element can each hold a part of the rename. A Kotlin file and its facade class are one
   * such pair.
   *
   * It replaces that method, which calls the processors directly and so can reach a dialog. It drops
   * the [myForceShowPreview] part of it: only [BaseRefactoringProcessor.doRun] reads that field, and
   * a headless rename never calls it.
   */
  private fun prepareRenamingWithoutUser(
    processors: List<HeadlessRenamePsiElementProcessor>,
    element: PsiElement,
    elementNewName: String,
    allRenames: MutableMap<PsiElement, String>,
  ) {
    for (processor in processors) {
      processor.prepareRenamingHeadless(element, elementNewName, allRenames)
    }
  }

  private fun psiModificationCount(): Long = PsiModificationTracker.getInstance(myProject).modificationCount

  @RequiresEdt
  internal fun applyPlan(
    usages: Array<UsageInfo>,
    affectedFiles: List<VirtualFile>,
    notes: List<String>,
    plannedPsiModificationCount: Long,
  ): HeadlessRenameResult {
    if (planApplied) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.PLAN_STALE, "The plan already ran.")
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments()
    if (psiModificationCount() != plannedPsiModificationCount) {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.PLAN_STALE, "The code changed after the plan was made.")
    }
    planApplied = true
    executeEx(usages)
    writeRefusal?.let {
      return HeadlessRenameResult.Failed(HeadlessRenameFailure.WRITE_FAILED, it.detail())
    }
    return if (performed) HeadlessRenameResult.Applied(usages.size, affectedFiles, skippedFiles.toList(), notes)
    else HeadlessRenameResult.Failed(HeadlessRenameFailure.UNKNOWN, "The write action did not reach the rename.")
  }

  /**
   * Runs the same passes as [RenameProcessor.preprocessUsages], with each question answered instead of asked.
   *
   * A conflict refuses through [collectedConflicts]. A name that [RenameUtil.checkRename] rejects refuses
   * through [refusalMessage]. The automatic-renamer pass is left out on purpose, because
   * [RenameProcessor] drives it from this method.
   */
  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    val usagesIn = refUsages.get()

    val conflicts = MultiMap<PsiElement, String>()
    RenameUtil.addConflictDescriptions(usagesIn, conflicts)
    headlessProcessors?.first?.findExistingNameConflictsHeadless(primaryElement, newName, conflicts, myAllRenames)
    if (!conflicts.isEmpty) {
      val conflictData = RefactoringEventData()
      conflictData.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values())
      myProject.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .conflictsDetected("refactoring.rename", conflictData)
      recordConflicts(conflicts)
      return false
    }

    val renamerUsages = if (applyAutomaticRenamers) automaticRenamerUsages(usagesIn) else emptyList()

    for ((element, elementNewName) in myAllRenames.entries.toList()) {
      if (element is PsiFile && isNewFileNameTaken(element, elementNewName)) {
        skippedFiles += element.virtualFile?.path ?: element.name
        myAllRenames.remove(element)
        continue
      }
      try {
        RenameUtil.checkRename(element, elementNewName)
      }
      catch (e: IncorrectOperationException) {
        refusalMessage = e.message
        return false
      }
    }

    val usagesSet = LinkedHashSet(usagesIn.asList())
    usagesSet.addAll(renamerUsages)
    RenameUtil.removeConflictUsages(usagesSet)?.let { dropped ->
      collectedNotes += "These usages keep the old name, because the new one collides: " +
                        dropped.joinToString(" ") { StringUtil.removeHtmlTags(it.description, true) }
    }
    refUsages.set(usagesSet.toTypedArray())

    val renameError = PsiElementRenameHandler.getRenameErrorMessage(myProject, null, primaryElement)
    if (renameError != null) {
      refusalMessage = renameError
      return false
    }
    return true
  }

  /**
   * Runs the automatic renamers with every question of them answered, and returns the usages they add.
   *
   * This repeats the renamer pass of [RenameProcessor.preprocessUsages], because that method keeps
   * its renamer list private. It takes the renamers the rename dialog would take:
   * - A factory with no option name asks nothing, and [RenameProcessor.findUsages] always takes it.
   * - A factory with an option name is a checkbox of the dialog. [RenameDialog] checks that box from
   *   `isEnabled`, which reads the settings of the IDE, and a person who presses Refactor accepts it.
   *   Then the dialog passes the factory to `addRenamerFactory`. No dialog means no such call, so this
   *   reads the same setting instead.
   *
   * The filter on [DumbService] is the one of the dialog too.
   */
  private fun automaticRenamerUsages(usages: Array<UsageInfo>): List<UsageInfo> {
    val dumbService = DumbService.getInstance(myProject)
    // A factory takes the usages of the one element it renames, as RenameProcessor.findUsages passes
    // them. The whole array would let a renamer add an element the interactive rename never touches.
    // Every unresolvable collision became a conflict above, so no usage here breaks classifyUsages.
    val classified = classifyUsages(myAllRenames.keys, usages.asList())

    val renamers = mutableListOf<AutomaticRenamer>()
    for ((element, elementNewName) in myAllRenames.entries.toList()) {
      val elementUsages = classified[element]
      for (factory in AutomaticRenamerFactory.EP_NAME.extensionList) {
        if (!dumbService.isUsableInCurrentContext(factory)) continue
        if (!factory.isApplicable(element)) continue
        if (factory.optionName != null && !factory.isEnabled) continue
        renamers += factory.createRenamer(element, elementNewName, elementUsages)
      }
    }
    renamers.retainAll { it.hasAnythingToRename() }
    if (renamers.isEmpty()) return emptyList()

    // The answer the dialog would give. RenameProcessor.showAutomaticRenamingDialog does the same in
    // a unit test.
    for (renamer in renamers) {
      for (element in renamer.elements) {
        renamer.setRename(element, renamer.getNewName(element))
      }
    }

    val collectedUsages = mutableListOf<UsageInfo>()
    val skippedCollisions = mutableListOf<UnresolvableCollisionUsageInfo>()
    for (renamer in renamers) {
      renamer.findUsages(collectedUsages, false, false, skippedCollisions, myAllRenames)
    }
    // A collision drops the element of the renamer, and AutomaticRenamer states the reason here. The
    // rename of the symbol itself stands, so this is a note and not a refusal. RenameProcessor reports
    // the same reasons, in a balloon of the status bar, which a caller with no user cannot read.
    if (skippedCollisions.isNotEmpty()) {
      collectedNotes += "An automatic rename kept the old name, because the new one collides: " +
                        skippedCollisions.joinToString(" ") { StringUtil.removeHtmlTags(it.description, true) }
    }

    val addedRenames = LinkedHashMap<PsiElement, String>()
    for (renamer in renamers) {
      for (variable in renamer.elements) {
        val variableNewName = renamer.getNewName(variable) ?: continue
        addElement(variable, variableNewName)
        when (val processorLookup = headlessProcessorsOf(variable)) {
          is HeadlessProcessorLookup.Found ->
            prepareRenamingWithoutUser(processorLookup.processors, variable, variableNewName, addedRenames)
          is HeadlessProcessorLookup.Missing ->
            collectedNotes += "This variable has no headless rename support, so a related declaration " +
                              "of it keeps the old name: " + variableNewName + "."
        }
      }
    }
    if (addedRenames.isEmpty()) return collectedUsages

    for (element in addedRenames.keys) {
      RenameUtil.assertNonCompileElement(element)
    }
    myAllRenames.putAll(addedRenames)
    for ((element, elementNewName) in addedRenames) {
      collectedUsages += RenameUtil.findUsages(element, elementNewName, myRefactoringScope, false, false, myAllRenames,
                                               processorFor(element))
    }
    return collectedUsages
  }

  private fun recordConflicts(conflicts: MultiMap<PsiElement, String>) {
    for ((element, messages) in conflicts.entrySet()) {
      val filePath = element?.takeIf { it.isValid }?.containingFile?.virtualFile?.path
      for (message in messages) {
        collectedConflicts += HeadlessRenameConflict(StringUtil.removeHtmlTags(message ?: continue, true), filePath)
      }
    }
  }

  /**
   * The headless answer to the file-already-exists question that [RenameProcessor] asks the user.
   *
   * The file itself is not an answer of yes. A file system that ignores the case answers `findChild`
   * with the file itself, because `foo.txt` matches `Foo.txt`, and a rename of the case only is no
   * collision. The platform makes the same exception in two places:
   * `CopyFilesOrDirectoriesHandler.checkFileExist`, which asks the user, and
   * `LocalFileSystemBase.renameFile`, which writes.
   */
  private fun isNewFileNameTaken(psiFile: PsiFile, newFileName: String): Boolean {
    val directory = psiFile.containingDirectory ?: return false
    val existing = directory.virtualFile.findChild(newFileName) ?: return false
    return existing != psiFile.virtualFile
  }

  /** The files that hold a usage of the rename, and that cannot be written. */
  private fun readOnlyUsageFiles(usages: Array<UsageInfo>): List<String> {
    val result = linkedSetOf<String>()
    for (usage in usages) {
      if (usage.element == null || usage.isWritable) continue
      result += usage.virtualFile?.path ?: continue
    }
    return result.toList()
  }

  private fun readOnlyDeclarationFiles(descriptor: UsageViewDescriptor): List<String> {
    val result = linkedSetOf<String>()
    for (element in getElementsToWrite(descriptor)) {
      val file = element?.takeIf { it.isValid }?.containingFile?.virtualFile ?: continue
      if (!file.isWritable) result += file.path
    }
    return result.toList()
  }

  private fun affectedFiles(usages: Array<UsageInfo>, descriptor: UsageViewDescriptor): List<VirtualFile> {
    val result = linkedSetOf<VirtualFile>()
    for (usage in usages) {
      result += usage.virtualFile ?: continue
    }
    for (element in descriptor.elements) {
      result += element?.takeIf { it.isValid }?.containingFile?.virtualFile ?: continue
    }
    return result.toList()
  }

  /**
   * True, and it reads no data context.
   *
   * [BaseRefactoringProcessor.isGlobalUndoAction] asks [com.intellij.ide.DataManager] for the focused
   * editor. A headless application has no such service, and the call there fails with a
   * `NullPointerException`. A headless caller also has no editor, so the base method would answer
   * true anyway. A rename that spans several files belongs in the global undo stack.
   */
  override fun isGlobalUndoAction(): Boolean = true

  override fun previewRefactoring(usages: Array<UsageInfo>) {
    // Reaching this means a check in plan() missed a case that doRun answers with the Find Usages window.
    throw IllegalStateException("A headless rename must not fall back to the Find Usages preview")
  }

  /**
   * Records that the language refused to rename [element], instead of showing the message.
   *
   * [RenameProcessor.performRefactoring] calls this and then returns, so the code holds a part of the
   * rename. [applyPlan] reports that through [HeadlessRenameFailure.WRITE_FAILED]. It does not call
   * the base method, which opens an error dialog a caller with no user cannot read.
   */
  override fun showRenameErrorMessage(e: IncorrectOperationException, element: PsiElement) {
    LOG.warn("The rename of $element was refused", e)
    writeRefusal = WriteRefusal(e.message, renamedBefore(element), myAllRenames.size)
  }

  /** The number of elements the platform renamed before it reached [element]. */
  private fun renamedBefore(element: PsiElement): Int =
    myAllRenames.keys.indexOfFirst { it === element }.coerceAtLeast(0)

  /**
   * Renames every element of the plan, and reports a refusal of the write instead of hiding it.
   *
   * The platform writes the rename. This adds the two answers a caller with no user needs: an invalid
   * element refuses before the first write, and a throwable of the write becomes a report.
   */
  override fun performRefactoring(usages: Array<UsageInfo>) {
    // The platform reports an invalid element, renames the rest, and so writes a part of the rename.
    // A caller with no user cannot see that report, so this refuses before the first write.
    myAllRenames.keys.firstOrNull { !it.isValid }?.let {
      writeRefusal = WriteRefusal("The element $it is no longer valid.", 0, myAllRenames.size)
      return
    }
    try {
      super.performRefactoring(usages)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("The rename of $primaryElement stopped while it wrote", e)
      //provides notifications
      writeRefusal = WriteRefusal(e.message, renamedBefore(primaryElement), myAllRenames.size)
      return
    }
    performed = writeRefusal == null
  }
}

/** Why the write of a rename stopped, and what it had written by then. */
private class WriteRefusal(message: String?, private val renamedElements: Int, private val plannedElements: Int) {
  /** The message of the refusal, as one sentence. */
  private val reason: String =
    message?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith(".")) it else "$it." }
    ?: "The language reported no reason."

  fun detail(): String {
    if (renamedElements == 0) return reason
    return "$reason The rename had written $renamedElements of $plannedElements elements, so a part of it stays in the code."
  }
}

/**
 * A rename that is ready to run, with the blast radius it reports.
 *
 * A caller can read [affectedFiles] and take a snapshot of their text before [apply], which is how a
 * language server builds a diff.
 */
@ApiStatus.Experimental
class RenamePlan internal constructor(
  private val driver: HeadlessRenameDriver,
  private val usageInfos: Array<UsageInfo>,
  private val primaryElementPointer: SmartPsiElementPointer<PsiElement>,
  /** The files that hold a usage or a declaration the rename is about to change. */
  val affectedFiles: List<VirtualFile>,
  /** What the caller must know before it applies the plan. Empty in the usual case. */
  val notes: List<String>,
  /** The PSI of the project as it was when the plan was made. */
  private val psiModificationCount: Long,
) {
  /**
   * The element the rename starts from, after the substitution.
   *
   * It differs from the target a caller passed to [HeadlessRenameProcessor.analyze] when the target
   * is an override, a constructor or a property accessor. Report this element to the caller, because
   * it names what the rename really changes.
   *
   * A plan lives between two calls by design, so it holds a pointer and not the element. Null means
   * the code changed after the plan was made, and [apply] then reports
   * [HeadlessRenameFailure.PLAN_STALE].
   */
  val primaryElement: PsiElement? get() = primaryElementPointer.element

  /**
   * Every usage the rename is about to update.
   *
   * A caller which reports the blast radius reads them, and one which drives its own refactoring
   * pipeline hands them to it. [apply] updates these usages, and no other.
   */
  val usages: List<UsageInfo> get() = usageInfos.asList()

  /** The number of usages the rename is about to update. */
  val affectedUsages: Int get() = usageInfos.size

  /**
   * Applies the rename.
   *
   * A plan runs once, and it describes the PSI of the moment it was made. A second call, and a call
   * after a change of the PSI, writes nothing and reports [HeadlessRenameFailure.PLAN_STALE]. It
   * commits the documents before it compares, so a change that has not reached the PSI yet counts as
   * a change. The caller holds the write-intent lock, because that commit needs it.
   */
  @RequiresEdt
  fun apply(): HeadlessRenameResult = driver.applyPlan(usageInfos, affectedFiles, notes, psiModificationCount)
}

/** What a headless rename did, or why it did nothing. */
@ApiStatus.Experimental
sealed interface HeadlessRenameResult {
  /** [HeadlessRenameProcessor.analyze] finished, and nothing was written. */
  data class Planned(val plan: RenamePlan) : HeadlessRenameResult

  /**
   * The rename was applied.
   *
   * @param affectedFiles the files that held a usage or a renamed declaration
   * @param skippedFiles a file rename that was dropped, because the new name was already taken
   */
  data class Applied(
    val affectedUsages: Int,
    val affectedFiles: List<VirtualFile>,
    val skippedFiles: List<String>,
    val notes: List<String>,
  ) : HeadlessRenameResult

  /** A conflict refused the rename. Nothing was written. */
  data class Refused(val conflicts: List<HeadlessRenameConflict>) : HeadlessRenameResult

  /**
   * The rename could not run. Nothing was written, unless [kind] is
   * [HeadlessRenameFailure.WRITE_FAILED]. That kind states what it wrote in [detail].
   */
  data class Failed(val kind: HeadlessRenameFailure, val detail: String?) : HeadlessRenameResult
}

/**
 * What a headless rename does with a usage in a file that cannot be written.
 *
 * It states nothing about a declaration to rename. A declaration in such a file refuses the rename
 * under both policies, because a rename that skips a declaration is a part of a rename.
 *
 * A file that only the VCS holds is refused too. A rename in the IDE calls
 * `CommonRefactoringUtil.checkReadOnlyStatus`, which checks the file out and then writes it. A
 * headless rename does not, because a tool with no user must not touch the VCS on its own. Check the
 * file out first, then call [HeadlessRenameProcessor.analyze] again.
 */
@ApiStatus.Experimental
enum class ReadOnlyUsagePolicy {
  /**
   * Refuse the whole rename, and write nothing.
   */
  REFUSE,

  /**
   * Drop the usage, rename the rest, and report the files in [RenamePlan.notes].
   */
  SKIP,
}

/** One conflict that [HeadlessRenameProcessor] found, with the HTML markup removed. */
@ApiStatus.Experimental
data class HeadlessRenameConflict(val description: String, val filePath: String?)

/**
 * Why a headless rename wrote nothing.
 *
 * A kind exists here only when a caller can act on it. Two reasons with one answer are one kind, and
 * [HeadlessRenameResult.Failed.detail] tells them apart for a person who reads the report.
 */
@ApiStatus.Experimental
enum class HeadlessRenameFailure {
  /**
   * The target cannot be renamed.
   *
   * It is compiled, it is a library symbol, it is no longer valid, or the language refuses it.
   */
  TARGET_NOT_RENAMABLE,

  /**
   * The rename processor of the target states no headless rename support.
   *
   * Its rename can ask the user a question, and a headless caller cannot answer one. The processor
   * needs a [HeadlessRenamePsiElementProcessor] registration.
   */
  LANGUAGE_NOT_SUPPORTED,

  /** A reference to the symbol belongs to a language that cannot take part in the rename. */
  UNSUPPORTED_REFERENCE_LANGUAGE,

  /** The indexes were not ready, so the usage set would be incomplete. */
  INDEX_NOT_READY,

  /** The new name is not valid for the symbol. */
  NEW_NAME_REFUSED,

  /** A usage or a declaration sits in a file that cannot be written. */
  READ_ONLY_USAGES,

  /** The use scope holds an unloaded module, so some usages cannot be updated. */
  INCOMPLETE_USE_SCOPE,

  /**
   * The plan no longer describes the code, so build a new one with [HeadlessRenameProcessor.analyze].
   *
   * Either the PSI changed after the plan was made, or the plan already ran.
   */
  PLAN_STALE,

  /**
   * The write of the rename stopped, and every check before it passed.
   *
   * The file system or the language refused a step. This is the one kind that can leave a part of the
   * rename in the code, and the detail says whether it did. Read the files again. One undo takes the
   * whole rename back, because it is one command.
   */
  WRITE_FAILED,

  /** No specific reason was reported. The detail and the log hold what there is. */
  UNKNOWN,
}
