// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.HintAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorActivityManager;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SlowOperations;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShowAutoImportPass extends TextEditorHighlightingPass {
  private final Editor myEditor;

  private final PsiFile myFile;

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean hasDirtyTextRange;

  ShowAutoImportPass(@NotNull Project project, @NotNull final PsiFile file, @NotNull Editor editor) {
    super(project, editor.getDocument(), false);
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    TextRange range = VisibleHighlightingPassFactory.calculateVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = file;

    hasDirtyTextRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL) != null;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().invokeLater(this::showImports);
  }

  private void showImports() {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (!EditorActivityManager.getInstance().isFocused(myEditor)) return;
    if (DumbService.isDumb(myProject) || !myFile.isValid()) return;
    if (myEditor.isDisposed() || myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid()) return;

    SlowOperations.allowSlowOperations(() -> doShowImports());
  }

  private void doShowImports() {
    int caretOffset = myEditor.getCaretModel().getOffset();
    importUnambiguousImports(caretOffset);
    if (isImportHintEnabled()) {
      List<HighlightInfo> visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset, myProject, myEditor, hasDirtyTextRange);

      for (int i = visibleHighlights.size() - 1; i >= 0; i--) {
        HighlightInfo info = visibleHighlights.get(i);
        if (info.startOffset <= caretOffset && showAddImportHint(info)) return;
      }

      for (HighlightInfo visibleHighlight : visibleHighlights) {
        if (visibleHighlight.startOffset > caretOffset && showAddImportHint(visibleHighlight)) return;
      }
    }
  }

  private void importUnambiguousImports(final int caretOffset) {
    Document document = myEditor.getDocument();
    final List<HighlightInfo> infos = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(document, myProject, null, 0, document.getTextLength(), info -> {
      if (info.hasHint() && info.getSeverity() == HighlightSeverity.ERROR && !info.getFixTextRange().containsOffset(caretOffset)) {
        infos.add(info);
      }
      return true;
    });

    for (HighlightInfo info : infos) {
      for (HintAction action : extractHints(info)) {
        if (action.isAvailable(myProject, myEditor, myFile)
            && mayAutoImportNow(myFile)
            && action.fixSilently(myEditor)) {
          break;
        }
      }
    }
  }

  public static boolean mayAutoImportNow(@NotNull PsiFile psiFile) {
    return isAddUnambiguousImportsOnTheFlyEnabled(psiFile) &&
           (ApplicationManager.getApplication().isUnitTestMode() || DaemonListeners.canChangeFileSilently(psiFile)) &&
           isInModelessContext(psiFile.getProject());
  }

  private static boolean isInModelessContext(@NotNull Project project) {
    return Registry.is("ide.perProjectModality") ?
           !LaterInvocator.isInModalContextForProject(project) :
           !LaterInvocator.isInModalContext();
  }

  public static boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile psiFile) {
    PsiFile templateFile = PsiUtilCore.getTemplateLanguageFile(psiFile);
    if (templateFile == null) return false;
    return ContainerUtil.exists(ReferenceImporter.EP_NAME.getExtensionList(), importer -> importer.isAddUnambiguousImportsOnTheFlyEnabled(psiFile));
  }

  @NotNull
  private static List<HighlightInfo> getVisibleHighlights(final int startOffset,
                                                          final int endOffset,
                                                          @NotNull Project project,
                                                          @NotNull Editor editor,
                                                          boolean isDirty) {
    final List<HighlightInfo> highlights = new ArrayList<>();
    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, null, startOffset, endOffset, info -> {
      //no changes after escape => suggest imports under caret only
      if (!isDirty && !info.getFixTextRange().contains(offset)) {
        return true;
      }
      if (info.hasHint() && !editor.getFoldingModel().isOffsetCollapsed(info.startOffset)) {
        highlights.add(info);
      }
      return true;
    });
    return highlights;
  }

  private boolean showAddImportHint(@NotNull HighlightInfo info) {
    PsiElement element = myFile.findElementAt(info.startOffset);
    if (element == null || !element.isValid()) return false;

    for (HintAction action : extractHints(info)) {
      if (action.isAvailable(myProject, myEditor, myFile) && action.showHint(myEditor)) {
        return true;
      }
    }
    return false;
  }

  public static void fixAllImportsSilently(@NotNull PsiFile file, @NotNull List<? extends HintAction> actions) {
    if (actions.isEmpty()) return;
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return;
    Editor editor = new ImaginaryEditor(file.getProject(), document);
    for (HintAction action : actions) {
      action.fixSilently(editor);
    }
  }

  /**
   * Run syntax highlighting and extract hint actions from resulting quick fixes. e.g. import suggestions.
   * Must be run outside EDT.
   */
  @NotNull
  public static List<HintAction> getImportHints(@NotNull PsiFile file) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // really can't run highlighting from within EDT
      // also, guard against recursive call optimize imports->add imports->optimize imports (in AddImportAction.doAddImport())
      throw new IllegalStateException("Must not be run from within EDT"); //return Collections.emptyList();
    }
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null || InjectedLanguageManager.getInstance(project).isInjectedFragment(file) || !hasUnresolvedReferences(file)) {
      return Collections.emptyList();
    }

    List<HintAction> result = new ArrayList<>();
    HighlightInfo fakeInfo = new HighlightInfo(null, null, HighlightInfoType.ERROR, 0, 0,
                                           null, null, HighlightSeverity.ERROR, false,
                                           null, false, 0, null,
                                           null, null, -1);
    QuickFixActionRegistrarImpl registrar = new QuickFixActionRegistrarImpl(fakeInfo);
    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        ProgressManager.checkCanceled();
        if (element instanceof PsiReference && ((PsiReference)element).resolve() == null) {
          UnresolvedReferenceQuickFixProvider.registerReferenceFixes((PsiReference)element, registrar);
        }
        super.visitElement(element);
      }
    });
    if (fakeInfo.quickFixActionRanges != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> marker : fakeInfo.quickFixActionRanges) {
        ProgressManager.checkCanceled();
        IntentionAction action = marker.first.getAction();
        if (action instanceof HintAction && action.isAvailable(project, null, file)) {
          result.add((HintAction)action);
        }
      }
    }
    return result;
  }

  private static boolean hasUnresolvedReferences(@NotNull PsiFile file) {
    if (file instanceof PsiCompiledElement) return false;
    Ref<Boolean> result = new Ref<>(false);
    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        for (PsiReference reference : element.getReferences()) {
          if (reference.resolve() == null) {
            result.set(true);
            stopWalking();
            break;
          }
        }
        super.visitElement(element);
      }
    });
    return result.get();
  }

  private boolean isImportHintEnabled() {
    return DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled() &&
           DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile);
  }

  @NotNull
  private static List<HintAction> extractHints(@NotNull HighlightInfo info) {
    List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> list = info.quickFixActionRanges;
    if (list == null) return Collections.emptyList();

    List<HintAction> hintActions = new SmartList<>();
    for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : list) {
      IntentionAction action = pair.getFirst().getAction();
      if (action instanceof HintAction) {
        hintActions.add((HintAction)action);
      }
    }
    return hintActions;
  }


  @NotNull
  public static @NlsContexts.HintText String getMessage(final boolean multiple, @NotNull String name) {
    final String messageKey = multiple ? "import.popup.multiple" : "import.popup.text";
    String hintText = DaemonBundle.message(messageKey, name);
    hintText +=
      " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    return hintText;
  }
}
