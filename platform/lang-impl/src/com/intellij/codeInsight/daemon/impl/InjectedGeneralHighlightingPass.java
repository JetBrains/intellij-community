// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.IdeBundle;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Perform injections, run highlight visitors and annotators on discovered injected files
 */
final class InjectedGeneralHighlightingPass extends ProgressableTextEditorHighlightingPass {
  private final @Nullable List<? extends @NotNull TextRange> myReducedRanges;
  private final boolean myUpdateAll;
  private final ProperTextRange myPriorityRange;
  private final @NotNull EditorColorsScheme myGlobalScheme;
  private final List<HighlightInfo> myHighlights = new ArrayList<>(); // guarded by myHighlights
  private final boolean myRunAnnotators;
  private final boolean myRunVisitors;
  private final boolean myHighlightErrorElements;
  private final HighlightInfoUpdater myHighlightInfoUpdater;

  InjectedGeneralHighlightingPass(@NotNull PsiFile psiFile,
                                  @NotNull Document document,
                                  @Nullable List<? extends @NotNull TextRange> reducedRanges,
                                  int startOffset,
                                  int endOffset,
                                  boolean updateAll,
                                  @NotNull ProperTextRange priorityRange,
                                  @Nullable Editor editor,
                                  boolean runAnnotators, boolean runVisitors, boolean highlightErrorElements, @NotNull HighlightInfoUpdater highlightInfoUpdater) {
    super(psiFile.getProject(), document, IdeBundle.message("highlighting.pass.injected.presentable.name"), psiFile, editor, TextRange.create(startOffset, endOffset), true, HighlightInfoProcessor.getEmpty());
    myReducedRanges = reducedRanges;
    myUpdateAll = updateAll;
    myPriorityRange = priorityRange;
    myGlobalScheme = editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
    myRunAnnotators = runAnnotators;
    myRunVisitors = runVisitors;
    myHighlightErrorElements = highlightErrorElements;
    myHighlightInfoUpdater = highlightInfoUpdater;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    if (!Registry.is("editor.injected.highlighting.enabled")) return;

    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myFile, myRestrictRange, myPriorityRange, GeneralHighlightingPass.SHOULD_HIGHLIGHT_FILTER, new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> allInsideElements = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.inside()));
    List<PsiElement> allOutsideElements = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.outside()));

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    TextAttributesKey fragmentKey = EditorColors.createInjectedLanguageFragmentKey(myFile.getLanguage());
    Set<@NotNull PsiFile> injected = ConcurrentCollectionFactory.createConcurrentSet();  // in case of concatenation, multiple hosts can return the same injected fragment. have to visit it only once
    ManagedHighlighterRecycler.runWithRecycler(getHighlightingSession(), recycler -> {
      processInjectedPsiFiles(allInsideElements, allOutsideElements, progress, injected,
                              (injectedPsi, places) ->
        runAnnotatorsAndVisitorsOnInjectedPsi(injectedLanguageManager, injectedPsi, places, fragmentKey, (toolId, psiElement, infos) -> {
          myHighlightInfoUpdater.psiElementVisited(toolId, psiElement, infos, getDocument(), injectedPsi, myProject, getHighlightingSession(), recycler);
          if (!infos.isEmpty()) {
            synchronized (myHighlights) {
              myHighlights.addAll(infos);
            }
          }
        })
      );
    });

    synchronized (myHighlights) {
      // injections were re-calculated, remove highlights stuck in highlightInfoUpdater from the previous invalid injection fragments
      myHighlightInfoUpdater.removeInfosForInjectedFilesOtherThan(myFile, myRestrictRange, getHighlightingSession(), injected);
    }
  }

  private void processInjectedPsiFiles(@NotNull List<? extends PsiElement> elements1,
                                       @NotNull List<? extends PsiElement> elements2,
                                       @NotNull ProgressIndicator progress,
                                       @NotNull Set<? super PsiFile> visitedInjected,
                                       @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InjectedLanguageManagerImpl injectedLanguageManager = InjectedLanguageManagerImpl.getInstanceImpl(myProject);
    List<DocumentWindow> cachedInjected = injectedLanguageManager.getCachedInjectedDocumentsInRange(myFile, myFile.getTextRange());
    Collection<PsiElement> hosts = new HashSet<>(elements1.size() + elements2.size() + cachedInjected.size());

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
    //rehighlight all injected PSI regardless the range,
    //since change in one place can lead to invalidation of injected PSI in the (completely) other place.
    for (DocumentWindow documentRange : cachedInjected) {
      ProgressManager.checkCanceled();
      if (!documentRange.isValid()) continue;
      PsiFile file = psiDocumentManager.getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = injectedLanguageManager.getInjectionHost(file);
      if (context != null
          && context.isValid()
          && !file.getProject().isDisposed()
          && (myUpdateAll || myRestrictRange.contains(context.getTextRange()))) { // consider strict if partial update
        if (myReducedRanges != null && !ContainerUtil.exists(myReducedRanges, reducedRange -> reducedRange.contains(context.getTextRange()))) { // skip if not in reduced
          continue;
        }
        hosts.add(context);
      }
    }

    Processor<PsiElement> collectInjectableProcessor = new CommonProcessors.CollectProcessor<>(hosts) {
      @Override
      public boolean process(PsiElement t) {
        ProgressManager.checkCanceled();
        if (InjectedLanguageUtil.isInjectable(t, false)) {
          super.process(t);
        }
        return true;
      }
    };
    injectedLanguageManager.processInjectableElements(elements1, collectInjectableProcessor);
    injectedLanguageManager.processInjectableElements(elements2, collectInjectableProcessor);

    // the most expensive process is running injectors for these hosts, comparing to highlighting the resulting injected fragments,
    // so instead of showing "highlighted 1% of injected fragments", show "ran injectors for 1% of hosts"
    setProgressLimit(hosts.size());

    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(hosts), progress, element -> {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        injectedLanguageManager.enumerateEx(element, myFile, false, (injectedPsi, places) -> {
          if (visitedInjected.add(injectedPsi)) {
            visitor.visit(injectedPsi, places);
          }
        });
        advanceProgress(1);
        return true;
      })) {
      throw new ProcessCanceledException();
    }
  }

  private @NotNull HighlightInfoHolder createInfoHolder(@NotNull PsiFile injectedPsi) {
    HighlightInfoFilter[] filters = HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensionList().toArray(HighlightInfoFilter.EMPTY_ARRAY);
    EditorColorsScheme actualScheme = getColorsScheme() == null ? EditorColorsManager.getInstance().getGlobalScheme() : getColorsScheme();
    return new HighlightInfoHolder(injectedPsi, filters) {
      @Override
      public @NotNull TextAttributesScheme getColorsScheme() {
        return actualScheme;
      }
    };
  }

  private void runAnnotatorsAndVisitorsOnInjectedPsi(@NotNull InjectedLanguageManager injectedLanguageManager,
                                                     @NotNull PsiFile injectedPsi,
                                                     @NotNull List<? extends PsiLanguageInjectionHost.Shred> places,
                                                     @Nullable TextAttributesKey attributesKey,
                                                     @NotNull ResultSink resultSink) {
    DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
    if (documentWindow == null) return;
    highlightInjectedBackground(injectedPsi, places, attributesKey, resultSink);

    AnnotationSession session = AnnotationSessionImpl.create(injectedPsi);
    GeneralHighlightingPass.setupAnnotationSession(session, myPriorityRange, myRestrictRange,
                                                   ((HighlightingSessionImpl)getHighlightingSession()).getMinimumSeverity());

    AnnotatorRunner annotatorRunner = myRunAnnotators ? new AnnotatorRunner(injectedPsi, false, session) : null;
    Divider.divideInsideAndOutsideAllRoots(injectedPsi, injectedPsi.getTextRange(), injectedPsi.getTextRange(), GeneralHighlightingPass.SHOULD_HIGHLIGHT_FILTER, dividedElements -> {
      List<? extends @NotNull PsiElement> inside = dividedElements.inside();
      LongList insideRanges = dividedElements.insideRanges();
      Runnable runnable = () -> {
        HighlightVisitorRunner highlightVisitorRunner = new HighlightVisitorRunner(injectedPsi, myGlobalScheme, myRunVisitors, myHighlightErrorElements);

        highlightVisitorRunner.createHighlightVisitorsFor(visitors -> {
          int chunkSize = Math.max(1, inside.size() / 100); // one percent precision is enough
          highlightVisitorRunner.runVisitors(injectedPsi, injectedPsi.getTextRange(), inside,
                                               insideRanges, List.of(), LongList.of(), visitors, false, chunkSize, true,
                                               () -> createInfoHolder(injectedPsi), (toolId, psiElement, infos) -> {
              // convert injected infos to host
              List<? extends HighlightInfo> hostInfos = infos.isEmpty()
                                                        ? infos
                                                        : ContainerUtil.flatMap(infos, info -> createPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager));
              resultSink.accept(toolId, psiElement, hostInfos);
            });
        });
        highlightInjectedSyntax(injectedPsi, places, resultSink);
      };
      if (annotatorRunner == null) {
        runnable.run();
      }
      else {
        annotatorRunner.runAnnotatorsAsync(inside, List.of(), runnable, resultSink);
      }
      return true;
    });
  }

  private static void highlightInjectedBackground(@NotNull PsiFile injectedPsi,
                                                  @NotNull List<? extends PsiLanguageInjectionHost.Shred> places,
                                                  @Nullable TextAttributesKey attributesKey,
                                                  @NotNull ResultSink resultSink) {
    boolean addTooltips = places.size() < 100;
    List<HighlightInfo> result = new ArrayList<>(places.size());
    for (PsiLanguageInjectionHost.Shred place : places) {
      PsiLanguageInjectionHost host = place.getHost();
      if (host == null) continue;
      TextRange textRange = place.getRangeInsideHost().shiftRight(host.getTextRange().getStartOffset());
      if (textRange.isEmpty()) continue;
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_BACKGROUND).range(textRange);
      if (attributesKey != null && InjectedLanguageUtil.isHighlightInjectionBackground(host)) {
        builder.textAttributes(attributesKey);
      }
      if (addTooltips) {
        String desc = injectedPsi.getLanguage().getDisplayName() + ": " + injectedPsi.getText();
        builder.unescapedToolTip(desc);
      }
      HighlightInfo info = builder.createUnconditionally();
      info.markFromInjection();
      info.toolId = InjectedLanguageManagerImpl.INJECTION_BACKGROUND_TOOL_ID;
      result.add(info);
    }
    resultSink.accept(InjectedLanguageManagerImpl.INJECTION_BACKGROUND_TOOL_ID, injectedPsi, result);
  }

  private static List<HighlightInfo> createPatchedInfos(@NotNull HighlightInfo info,
                                                        @NotNull PsiFile injectedPsi,
                                                        @NotNull DocumentWindow documentWindow,
                                                        @NotNull InjectedLanguageManager injectedLanguageManager) {
    ProperTextRange infoRange = new ProperTextRange(info.startOffset, info.endOffset);
    List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, infoRange);
    List<HighlightInfo> result = new ArrayList<>(editables.size());
    for (TextRange editable : editables) {
      TextRange hostRange = documentWindow.injectedToHost(editable);

      boolean isAfterEndOfLine = info.isAfterEndOfLine();
      if (isAfterEndOfLine) {
        // convert injected afterEndOfLine to either host's afterEndOfLine or not-afterEndOfLine highlight of the injected fragment boundary
        int hostEndOffset = hostRange.getEndOffset();
        int lineNumber = documentWindow.getDelegate().getLineNumber(hostEndOffset);
        int hostLineEndOffset = documentWindow.getDelegate().getLineEndOffset(lineNumber);
        if (hostEndOffset < hostLineEndOffset) {
          // convert to non-afterEndOfLine
          isAfterEndOfLine = false;
          hostRange = new ProperTextRange(hostRange.getStartOffset(), hostEndOffset+1);
        }
      }

      HighlightInfo patched =
        new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey, info.type,
                          hostRange.getStartOffset(), hostRange.getEndOffset(),
                          info.getDescription(), info.getToolTip(), info.getSeverity(), isAfterEndOfLine, null,
                          false, 0, info.getProblemGroup(), info.toolId, info.getGutterIconRenderer(), info.getGroup(), info.unresolvedReference);
      patched.setHint(info.hasHint());

      info.findRegisteredQuickFix((descriptor, quickfixTextRange) -> {
        List<TextRange> editableQF = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, quickfixTextRange);
        for (TextRange editableRange : editableQF) {
          TextRange hostEditableRange = documentWindow.injectedToHost(editableRange);
          patched.registerFix(descriptor.getAction(), descriptor.myOptions, descriptor.getDisplayName(), hostEditableRange, descriptor.myKey);
        }
        return null;
      });
      patched.markFromInjection();
      result.add(patched);
    }
    return result;
  }

  private void highlightInjectedSyntax(@NotNull PsiFile injectedPsi,
                                       @NotNull List<? extends PsiLanguageInjectionHost.Shred> places,
                                       @NotNull ResultSink resultSink) {
    List<HighlightInfo> result = new ArrayList<>(places.size()*2);
    InjectedLanguageUtil.processTokens(injectedPsi, places, (@NotNull TextRange hostRange, TextAttributesKey @NotNull [] keys) -> {
      List<HighlightInfo> infos = InjectedLanguageFragmentSyntaxUtil.addSyntaxInjectedFragmentInfo(myGlobalScheme, hostRange, keys, InjectedLanguageManagerImpl.INJECTION_SYNTAX_TOOL_ID);
      result.addAll(infos);
    });
    resultSink.accept(InjectedLanguageManagerImpl.INJECTION_SYNTAX_TOOL_ID, injectedPsi, result);
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    synchronized (myHighlights) {
      return myHighlights;
    }
  }

  @Override
  protected void applyInformationWithProgress() {
  }
}
