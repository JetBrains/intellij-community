/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.concurrency.JobLauncher;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InjectedGeneralHighlightingPass extends GeneralHighlightingPass implements DumbAware {
  private static final String PRESENTABLE_NAME = "Injected fragments";

  InjectedGeneralHighlightingPass(@NotNull Project project,
                                  @NotNull PsiFile file,
                                  @NotNull Document document,
                                  int startOffset,
                                  int endOffset,
                                  boolean updateAll,
                                  @NotNull ProperTextRange priorityRange,
                                  @Nullable Editor editor,
                                  @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(project, file, document, startOffset, endOffset, updateAll, priorityRange, editor, highlightInfoProcessor);
  }

  @Override
  protected String getPresentableName() {
    return PRESENTABLE_NAME;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull final ProgressIndicator progress) {
    if (!Registry.is("editor.injected.highlighting.enabled")) return;

    final Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>(100);

    final List<PsiElement> inside = new ArrayList<PsiElement>();
    final List<PsiElement> outside = new ArrayList<PsiElement>();
    List<ProperTextRange> insideRanges = new ArrayList<ProperTextRange>();
    List<ProperTextRange> outsideRanges = new ArrayList<ProperTextRange>();
    //TODO: this thing is just called TWICE with same arguments eating CPU on huge files :(
    Divider.divideInsideAndOutside(myFile, myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), myPriorityRange, inside, insideRanges, outside,
                                   outsideRanges, false, SHOULD_HIGHLIGHT_FILTER);


    // all infos for the "injected fragment for the host which is inside" are indeed inside
    // but some of the infos for the "injected fragment for the host which is outside" can be still inside
    Set<HighlightInfo> injectedResult = new THashSet<HighlightInfo>();
    Set<PsiFile> injected = getInjectedPsiFiles(inside, outside, progress);
    setProgressLimit(injected.size());

    if (!addInjectedPsiHighlights(injected, progress, Collections.synchronizedSet(injectedResult))) {
      throw new ProcessCanceledException();
    }
    final List<HighlightInfo> injectionsOutside = new ArrayList<HighlightInfo>(gotHighlights.size());

    Set<HighlightInfo> result;
    synchronized (injectedResult) {
      // sync here because all writes happened in another thread
      result = injectedResult;
    }
    for (HighlightInfo info : result) {
      if (myRestrictRange.contains(info)) {
        gotHighlights.add(info);
      }
      else {
        // nonconditionally apply injected results regardless whether they are in myStartOffset,myEndOffset
        injectionsOutside.add(info);
      }
    }

    if (!injectionsOutside.isEmpty()) {
      final ProperTextRange priorityIntersection = myPriorityRange.intersection(myRestrictRange);
      if ((!inside.isEmpty() || !gotHighlights.isEmpty()) &&
          priorityIntersection != null) { // do not apply when there were no elements to highlight
        // clear infos found in visible area to avoid applying them twice
        final List<HighlightInfo> toApplyInside = new ArrayList<HighlightInfo>(gotHighlights);
        myHighlights.addAll(toApplyInside);
        gotHighlights.clear();

        myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, toApplyInside, myPriorityRange, myRestrictRange,
                                                                        getId());
      }

      List<HighlightInfo> toApply = new ArrayList<HighlightInfo>();
      for (HighlightInfo info : gotHighlights) {
        if (!myRestrictRange.containsRange(info.getStartOffset(), info.getEndOffset())) continue;
        if (!myPriorityRange.containsRange(info.getStartOffset(), info.getEndOffset())) {
          toApply.add(info);
        }
      }
      toApply.addAll(injectionsOutside);

      myHighlightInfoProcessor.highlightsOutsideVisiblePartAreProduced(myHighlightingSession, toApply, myRestrictRange, new ProperTextRange(0, myDocument.getTextLength()),
                                                                       getId());
    }
    else {
      // else apply only result (by default apply command) and only within inside
      myHighlights.addAll(gotHighlights);
      myHighlightInfoProcessor.highlightsInsideVisiblePartAreProduced(myHighlightingSession, myHighlights, myRestrictRange, myRestrictRange,
                                                                      getId());
    }
  }

  @NotNull
  private Set<PsiFile> getInjectedPsiFiles(@NotNull final List<PsiElement> elements1,
                                           @NotNull final List<PsiElement> elements2,
                                           @NotNull final ProgressIndicator progress) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final Set<PsiFile> outInjected = new THashSet<PsiFile>();

    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(myFile);
    final Collection<PsiElement> hosts = new THashSet<PsiElement>(elements1.size() + elements2.size() + injected.size());

    //rehighlight all injected PSI regardless the range,
    //since change in one place can lead to invalidation of injected PSI in (completely) other place.
    for (DocumentWindow documentRange : injected) {
      progress.checkCanceled();
      if (!documentRange.isValid()) continue;
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
      if (context != null
          && context.isValid()
          && !file.getProject().isDisposed()
          && (myUpdateAll || myRestrictRange.intersects(context.getTextRange()))) {
        hosts.add(context);
      }
    }
    InjectedLanguageManagerImpl injectedLanguageManager = InjectedLanguageManagerImpl.getInstanceImpl(myProject);
    Processor<PsiElement> collectInjectableProcessor = Processors.cancelableCollectProcessor(hosts);
    injectedLanguageManager.processInjectableElements(elements1, collectInjectableProcessor);
    injectedLanguageManager.processInjectableElements(elements2, collectInjectableProcessor);

    final PsiLanguageInjectionHost.InjectedPsiVisitor visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        synchronized (outInjected) {
          outInjected.add(injectedPsi);
        }
      }
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<PsiElement>(hosts), progress, true,
                                                                   element -> {
                                                                     ApplicationManager.getApplication().assertReadAccessAllowed();
                                                                     progress.checkCanceled();
                                                                     InjectedLanguageUtil.enumerate(element, myFile, false, visitor);
                                                                     return true;
                                                                   })) {
      throw new ProcessCanceledException();
    }
    synchronized (outInjected) {
      return outInjected;
    }
  }

  // returns false if canceled
  private boolean addInjectedPsiHighlights(@NotNull final Set<PsiFile> injectedFiles,
                                           @NotNull final ProgressIndicator progress,
                                           @NotNull final Collection<HighlightInfo> outInfos) {
    if (injectedFiles.isEmpty()) return true;
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    final TextAttributes injectedAttributes = myGlobalScheme.getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);

    return JobLauncher.getInstance()
      .invokeConcurrentlyUnderProgress(new ArrayList<PsiFile>(injectedFiles), progress, isFailFastOnAcquireReadAction(),
                                       injectedPsi -> addInjectedPsiHighlights(injectedPsi, injectedAttributes, outInfos, progress, injectedLanguageManager));
  }

  private boolean addInjectedPsiHighlights(@NotNull PsiFile injectedPsi,
                                           TextAttributes injectedAttributes,
                                           @NotNull Collection<HighlightInfo> outInfos,
                                           @NotNull ProgressIndicator progress,
                                           @NotNull InjectedLanguageManager injectedLanguageManager) {
    DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
    if (documentWindow == null) return true;
    Place places = InjectedLanguageUtil.getShreds(injectedPsi);
    boolean addTooltips = places.size() < 100;
    for (PsiLanguageInjectionHost.Shred place : places) {
      PsiLanguageInjectionHost host = place.getHost();
      if (host == null) continue;
      TextRange textRange = place.getRangeInsideHost().shiftRight(host.getTextRange().getStartOffset());
      if (textRange.isEmpty()) continue;
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_BACKGROUND).range(textRange);
      if (injectedAttributes != null && InjectedLanguageUtil.isHighlightInjectionBackground(host)) {
        builder.textAttributes(injectedAttributes);
      }
      if (addTooltips) {
        String desc = injectedPsi.getLanguage().getDisplayName() + ": " + injectedPsi.getText();
        builder.unescapedToolTip(desc);
      }
      HighlightInfo info = builder.createUnconditionally();
      info.setFromInjection(true);
      outInfos.add(info);
    }

    HighlightInfoHolder holder = createInfoHolder(injectedPsi);
    runHighlightVisitorsForInjected(injectedPsi, holder, progress);
    for (int i = 0; i < holder.size(); i++) {
      HighlightInfo info = holder.get(i);
      final int startOffset = documentWindow.injectedToHost(info.startOffset);
      final TextRange fixedTextRange = getFixedTextRange(documentWindow, startOffset);
      addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, fixedTextRange, outInfos);
    }
    int injectedStart = holder.size();
    highlightInjectedSyntax(injectedPsi, holder);
    for (int i = injectedStart; i < holder.size(); i++) {
      HighlightInfo info = holder.get(i);
      final int startOffset = info.startOffset;
      final TextRange fixedTextRange = getFixedTextRange(documentWindow, startOffset);
      if (fixedTextRange == null) {
        info.setFromInjection(true);
        outInfos.add(info);
      }
      else {
        HighlightInfo patched =
          new HighlightInfo(info.forcedTextAttributes, info.forcedTextAttributesKey,
                            info.type, fixedTextRange.getStartOffset(),
                            fixedTextRange.getEndOffset(),
                            info.getDescription(), info.getToolTip(), info.type.getSeverity(null),
                            info.isAfterEndOfLine(), null, false, 0, info.getProblemGroup(), info.getGutterIconRenderer());
        patched.setFromInjection(true);
        outInfos.add(patched);
      }
    }

    if (!isDumbMode()) {
      List<HighlightInfo> todos = new ArrayList<HighlightInfo>();
      highlightTodos(injectedPsi, injectedPsi.getText(), 0, injectedPsi.getTextLength(), progress, myPriorityRange, todos, todos);
      for (HighlightInfo info : todos) {
        addPatchedInfos(info, injectedPsi, documentWindow, injectedLanguageManager, null, outInfos);
      }
    }
    advanceProgress(1);
    return true;
  }

  @Nullable("null means invalid")
  private static TextRange getFixedTextRange(@NotNull DocumentWindow documentWindow, int startOffset) {
    final TextRange fixedTextRange;
    TextRange textRange = documentWindow.getHostRange(startOffset);
    if (textRange == null) {
      // todo[cdr] check this fix. prefix/suffix code annotation case
      textRange = findNearestTextRange(documentWindow, startOffset);
      if (textRange == null) return null;
      final boolean isBefore = startOffset < textRange.getStartOffset();
      fixedTextRange = new ProperTextRange(isBefore ? textRange.getStartOffset() - 1 : textRange.getEndOffset(),
                                     isBefore ? textRange.getStartOffset() : textRange.getEndOffset() + 1);
    }
    else {
      fixedTextRange = null;
    }
    return fixedTextRange;
  }

  private static void addPatchedInfos(@NotNull HighlightInfo info,
                                      @NotNull PsiFile injectedPsi,
                                      @NotNull DocumentWindow documentWindow,
                                      @NotNull InjectedLanguageManager injectedLanguageManager,
                                      @Nullable TextRange fixedTextRange,
                                      @NotNull Collection<HighlightInfo> out) {
    ProperTextRange textRange = new ProperTextRange(info.startOffset, info.endOffset);
    List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, textRange);
    for (TextRange editable : editables) {
      TextRange hostRange = fixedTextRange == null ? documentWindow.injectedToHost(editable) : fixedTextRange;

      boolean isAfterEndOfLine = info.isAfterEndOfLine();
      if (isAfterEndOfLine) {
        // convert injected afterEndOfLine to either host' afterEndOfLine or not-afterEndOfLine highlight of the injected fragment boundary
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
                          info.getDescription(), info.getToolTip(), info.type.getSeverity(null), isAfterEndOfLine, null, false, 0, info.getProblemGroup(), info.getGutterIconRenderer());
      patched.setHint(info.hasHint());

      if (info.quickFixActionRanges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
          TextRange quickfixTextRange = pair.getSecond();
          List<TextRange> editableQF = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, quickfixTextRange);
          for (TextRange editableRange : editableQF) {
            HighlightInfo.IntentionActionDescriptor descriptor = pair.getFirst();
            if (patched.quickFixActionRanges == null) patched.quickFixActionRanges = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>();
            TextRange hostEditableRange = documentWindow.injectedToHost(editableRange);
            patched.quickFixActionRanges.add(Pair.create(descriptor, hostEditableRange));
          }
        }
      }
      patched.setFromInjection(true);
      out.add(patched);
    }
  }

  // finds the first nearest text range
  @Nullable("null means invalid")
  private static TextRange findNearestTextRange(final DocumentWindow documentWindow, final int startOffset) {
    TextRange textRange = null;
    for (Segment marker : documentWindow.getHostRanges()) {
      TextRange curRange = ProperTextRange.create(marker);
      if (curRange.getStartOffset() > startOffset && textRange != null) break;
      textRange = curRange;
    }
    return textRange;
  }

  private void runHighlightVisitorsForInjected(@NotNull PsiFile injectedPsi,
                                               @NotNull final HighlightInfoHolder holder,
                                               @NotNull final ProgressIndicator progress) {
    HighlightVisitor[] filtered = getHighlightVisitors(injectedPsi);
    try {
      final List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      for (final HighlightVisitor visitor : filtered) {
        visitor.analyze(injectedPsi, true, holder, () -> {
          for (PsiElement element : elements) {
            progress.checkCanceled();
            visitor.visit(element);
          }
        });
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
  }

  private void highlightInjectedSyntax(@NotNull PsiFile injectedPsi, @NotNull HighlightInfoHolder holder) {
    List<Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange>> tokens = InjectedLanguageUtil
      .getHighlightTokens(injectedPsi);
    if (tokens == null) return;

    final Language injectedLanguage = injectedPsi.getLanguage();
    Project project = injectedPsi.getProject();
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(injectedLanguage, project, injectedPsi.getVirtualFile());
    final TextAttributes defaultAttrs = myGlobalScheme.getAttributes(HighlighterColors.TEXT);

    for (Trinity<IElementType, SmartPsiElementPointer<PsiLanguageInjectionHost>, TextRange> token : tokens) {
      ProgressManager.checkCanceled();
      IElementType tokenType = token.getFirst();
      PsiLanguageInjectionHost injectionHost = token.getSecond().getElement();
      if (injectionHost == null) continue;
      TextRange textRange = token.getThird();
      TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(tokenType);
      if (textRange.getLength() == 0) continue;

      TextRange annRange = textRange.shiftRight(injectionHost.getTextRange().getStartOffset());
      // force attribute colors to override host' ones
      TextAttributes attributes = null;
      for(TextAttributesKey key:keys) {
        TextAttributes attrs2 = myGlobalScheme.getAttributes(key);
        if (attrs2 != null) {
          attributes = attributes == null ? attrs2 : TextAttributes.merge(attributes, attrs2);
        }
      }
      TextAttributes forcedAttributes;
      if (attributes == null || attributes.isEmpty() || attributes.equals(defaultAttrs)) {
        forcedAttributes = TextAttributes.ERASE_MARKER;
      }
      else {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT).range(annRange).textAttributes(
          TextAttributes.ERASE_MARKER).createUnconditionally();
        holder.add(info);

        forcedAttributes = new TextAttributes(attributes.getForegroundColor(), attributes.getBackgroundColor(), 
                                              attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
      }

      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT).range(annRange).textAttributes(forcedAttributes)
          .createUnconditionally();
      holder.add(info);
    }
  }

  @Override
  protected void applyInformationWithProgress() {
  }
}
