// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.getUsageRanges
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.inlinePrompt.isInlinePromptShown
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.AstLoadingFilter
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IdentifierHighlightingComputer (
  private val myPsiFile: PsiFile,
  private val myEditor: Editor,
  private val myVisibleRange: ProperTextRange,
  private val myCaretOffset:Int
) {
  private val myEnabled: Boolean

  /**
   * @param myPsiFile may be injected fragment, in which case the `editor` must be corresponding injected editor and  `visibleRange` must have consistent offsets inside the injected document.
   * In both cases, [.doCollectInformation] will produce and apply HighlightInfos to the host file.
   */
  init {
    val model = myEditor.getCaretModel()
    val highlightSelectionOccurrences = myEditor.getSettings().isHighlightSelectionOccurrences()
    myEnabled = !highlightSelectionOccurrences || !model.getPrimaryCaret().hasSelection()
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  @ApiStatus.Internal
  fun computeRanges(): IdentifierHighlightingResult {
    if (myCaretOffset < 0 || !myEnabled || isInlinePromptShown(myEditor)) {
      thisLogger().debug("IdentifierHighlightingComputer.computeRanges empty $myCaretOffset $myEnabled ${isInlinePromptShown(myEditor)}")
      return EMPTY_RESULT
    }
    val myInfos: MutableCollection<IdentifierOccurrence> = LinkedHashSet()
    val myTargets: MutableCollection<Segment> = LinkedHashSet()

    val highlightUsagesHandler = HighlightUsagesHandler.createCustomHandler<PsiElement?>(myEditor, myPsiFile, myVisibleRange)
    var runFindUsages = true
    if (highlightUsagesHandler != null) {
      val targets = highlightUsagesHandler.getTargets()
      highlightUsagesHandler.computeUsages(targets)
      val readUsages = highlightUsagesHandler.readUsages
      for (readUsage in readUsages) {
        LOG.assertTrue(readUsage != null, "null text range from " + highlightUsagesHandler)
      }
      myInfos.addAll(readUsages.map { u: TextRange ->
        IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_READ)
      })
      val writeUsages = highlightUsagesHandler.writeUsages
      for (writeUsage in writeUsages) {
        LOG.assertTrue(writeUsage != null, "null text range from $highlightUsagesHandler")
      }
      myInfos.addAll(writeUsages.map { u: TextRange ->
        IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
      })
      if (!highlightUsagesHandler.highlightReferences()) {
        runFindUsages = false
      }
      val target: PsiElement? = findTarget(myEditor, myPsiFile, myCaretOffset)
      if (target != null) {
        myTargets.add(target.getTextRange())
      }
    }

    if (runFindUsages) {
      collectCodeBlockMarkerRanges(myInfos, myTargets)

      try {
        DumbService.getInstance(myPsiFile.project).withAlternativeResolveEnabled(
          Runnable {
            highlightReferencesAndDeclarations(myInfos, myTargets)
          })
      }
      catch (e: IndexNotReadyException) {
        logIndexNotReadyException(e)
        // Ignoring IndexNotReadyException.
        // We can't show a warning because this usage search is triggered automatically and user does not control it.
      }
    }
    val result = IdentifierHighlightingResult(myInfos, myTargets)
    val injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myPsiFile, myCaretOffset)
    val injectedFile = PsiDocumentManager.getInstance(myPsiFile.getProject()).getPsiFile(injectedEditor!!.getDocument())!!
    val injectedOffset = injectedEditor.getCaretModel().offset
    val injTargetSymbols = targetSymbols(injectedFile, injectedOffset)
    thisLogger().debug("IdentifierHighlightingComputer.computeRanges result $result; " +
                       "highlightUsagesHandler=$highlightUsagesHandler " +
                       "runFindUsages=$runFindUsages " +
                       "getTargetSymbols()=${getTargetSymbols()}; " +
                       "myPsiFile=$myPsiFile; " +
                       "myPsiFile.getViewProvider=${myPsiFile.getViewProvider()}; " +
                       "myCaretOffset=$myCaretOffset; " +
                       "hostTargetSymbols=${targetSymbols(myPsiFile, myCaretOffset)}; " +
                       "injectedEditor=${InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myPsiFile, myCaretOffset)}; " +
                       "injectedFile == myFile: ${injectedFile == myPsiFile}; " +
                       "injectedOffset=${injectedOffset}; " +
                       "injTargetSymbols=$injTargetSymbols")
    return result
  }

  /**
   * Collects code block markers ranges to highlight. E.g. if/elsif/else. Collected ranges will be highlighted the same way as braces
   */
  private fun collectCodeBlockMarkerRanges(
    myMarkupInfos: MutableCollection<in IdentifierOccurrence>,
    myTargets: MutableCollection<in TextRange>
  ) {
    val contextElement = myPsiFile.findElementAt(
      TargetElementUtil.adjustOffset(myPsiFile, myEditor.getDocument(), myCaretOffset))
    if (contextElement != null) {
      myTargets.add(contextElement.getTextRange())
      val manager = InjectedLanguageManager.getInstance(myPsiFile.getProject())
      for (range in CodeBlockSupportHandler.findMarkersRanges(contextElement)) {
        myMarkupInfos.add(IdentifierOccurrence(manager.injectedToHost(contextElement, range), HighlightInfoType.ELEMENT_UNDER_CARET_STRUCTURAL))
      }
    }
  }

  private fun highlightReferencesAndDeclarations(
    myMarkupInfos: MutableCollection<in IdentifierOccurrence>,
    myTargets: MutableCollection<in Segment>
  ) {
    val targetSymbols = getTargetSymbols()
    for (symbol in targetSymbols) {
      highlightTargetUsages(symbol, myMarkupInfos, myTargets, myPsiFile)
    }
  }

  fun highlightTargetUsages(
    target: Symbol,
    myMarkupInfos: MutableCollection<in IdentifierOccurrence>,
    myTargets: MutableCollection<in Segment>,
    myPsiFile: PsiFile
  ) {
    try {
      AstLoadingFilter.disallowTreeLoading<RuntimeException?>(ThrowableRunnable {
        val hostRanges = getUsageRanges(myPsiFile, target)
        if (hostRanges == null) {
          return@ThrowableRunnable
        }
        val reads = hostRanges.readRanges.map { u: TextRange ->
          IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_READ)
        }
        val readDecls = hostRanges.readDeclarationRanges.map { u: TextRange ->
          IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_READ)
        }
        val writes = hostRanges.writeRanges.map { u: TextRange ->
          IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
        }
        val writeDecls = hostRanges.writeDeclarationRanges.map { u: TextRange ->
          IdentifierOccurrence(u, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
        }
        myMarkupInfos.addAll(reads)
        myMarkupInfos.addAll(readDecls)
        myMarkupInfos.addAll(writes)
        myMarkupInfos.addAll(writeDecls)
        myTargets.addAll(reads.map { o -> o.range })
        myTargets.addAll(readDecls.map { o -> o.range })
        myTargets.addAll(writes.map { o -> o.range })
        myTargets.addAll(writeDecls.map { o -> o.range })
      }, {
        "Currently highlighted file: \n" +
        "psi file: " + myPsiFile + ";\n" +
        "virtual file: " + myPsiFile.getVirtualFile()
      })
    }
    catch (e: IndexNotReadyException) {
      Logger.getInstance(IdentifierHighlightingComputer::class.java).trace(e)
    }
  }

  private fun getTargetSymbols(): Collection<Symbol> {
    if (myCaretOffset < 0 || !myEnabled) {
      return listOf()
    }
    try {
      val fromHostFile: Collection<Symbol> = targetSymbols(myPsiFile, myCaretOffset)
      if (!fromHostFile.isEmpty()) {
        return fromHostFile
      }
    }
    catch (e: IndexNotReadyException) {
      logIndexNotReadyException(e)
    }
    val injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myPsiFile, myCaretOffset)
    val injectedFile = PsiDocumentManager.getInstance(myPsiFile.getProject()).getPsiFile(injectedEditor!!.getDocument())
    if (injectedFile == null || injectedFile === myPsiFile) {
      return listOf()
    }
    val injectedOffset = injectedEditor.getCaretModel().offset
    return targetSymbols(injectedFile, injectedOffset)
  }

  companion object {
    private val LOG = Logger.getInstance(IdentifierHighlightingComputer::class.java)

    private fun findTarget(editor: Editor, file: PsiFile, myCaretOffset: Int): PsiElement? {
      val offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), myCaretOffset)
      return file.findElementAt(offset)
    }

    /**
     * Returns read and write usages of psi element inside a single element
     *
     * @param target target psi element
     * @param psiElement psi element to search in
     */
    @JvmStatic
    fun getHighlightUsages(
      target: PsiElement,
      psiElement: PsiElement,
      withDeclarations: Boolean,
      readRanges: MutableCollection<in TextRange>,
      writeRanges: MutableCollection<in TextRange>
    ) {
      getUsages(target, psiElement, withDeclarations, true, readRanges, writeRanges)
    }

    /**
     * Returns usages of psi element inside a single element
     * @param target target psi element
     * @param psiElement psi element to search in
     */
    @JvmStatic
    fun getUsages(target: PsiElement, psiElement: PsiElement, withDeclarations: Boolean): Collection<TextRange> {
      val ranges: MutableList<TextRange> = ArrayList()
      getUsages(target, psiElement, withDeclarations, false, ranges, ranges)
      return ranges
    }

    private fun getUsages(
      target: PsiElement,
      scopeElement: PsiElement,
      withDeclarations: Boolean,
      detectAccess: Boolean,
      readRanges: MutableCollection<in TextRange>,
      writeRanges: MutableCollection<in TextRange>
    ) {
      val detector = if (detectAccess) ReadWriteAccessDetector.findDetector(target) else null
      val findUsagesManager = (FindManager.getInstance(target.getProject()) as FindManagerImpl).findUsagesManager
      val findUsagesHandler = findUsagesManager.getFindUsagesHandler(target, true)
      val scope = LocalSearchScope(scopeElement)
      val refs = if (findUsagesHandler == null)
        ReferencesSearch.search(target, scope).findAll()
      else
        findUsagesHandler.findReferencesToHighlight(target, scope)
      for (psiReference in refs) {
        if (psiReference == null) {
          LOG.error("Null reference returned, findUsagesHandler=" + findUsagesHandler + "; target=" + target + " of " + target.javaClass)
          continue
        }
        val destination: MutableCollection<in TextRange> = if (detector == null || detector.getReferenceAccess(target, psiReference) == ReadWriteAccessDetector.Access.Read) {
          readRanges
        }
        else {
          writeRanges
        }
        HighlightUsagesHandler.collectHighlightRanges(psiReference, destination)
      }

      if (withDeclarations) {
        val declRange = HighlightUsagesHandler.getNameIdentifierRange(scopeElement.getContainingFile(), target)
        if (declRange != null) {
          if (detector != null && detector.isDeclarationWriteAccess(target)) {
            writeRanges.add(declRange)
          }
          else {
            readRanges.add(declRange)
          }
        }
      }
    }

    private fun logIndexNotReadyException(e: IndexNotReadyException) {
      if (LOG.isTraceEnabled) {
        LOG.trace(e)
      }
    }
  }
}