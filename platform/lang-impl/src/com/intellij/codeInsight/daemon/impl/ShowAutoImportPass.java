// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

public class ShowAutoImportPass extends TextEditorHighlightingPass {
  private final Editor myEditor;

  private final PsiFile myFile;

  private final TextRange myVisibleRange;
  private final boolean hasDirtyTextRange;
  private final List<BooleanSupplier> autoImportActions = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean myInContent;

  ShowAutoImportPass(@NotNull PsiFile file, @NotNull Editor editor, @NotNull ProperTextRange visibleRange) {
    super(file.getProject(), editor.getDocument(), false);

    myEditor = editor;
    myVisibleRange = visibleRange;
    myFile = file;
    hasDirtyTextRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL) != null;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    Document document = myEditor.getDocument();
    List<HighlightInfo> infos = new ArrayList<>();
    List<BooleanSupplier> result = new ArrayList<>();
    int exceptCaretOffset = myEditor.getCaretModel().getOffset();
    VirtualFile virtualFile = myFile.getVirtualFile();
    myInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(myProject, virtualFile, false);

    DaemonCodeAnalyzerEx.processHighlights(document, myProject, null, 0, document.getTextLength(), info -> {
      if (info.isUnresolvedReference() && info.getSeverity() == HighlightSeverity.ERROR && !info.containsOffset(exceptCaretOffset, true)) {
        infos.add(info);
      }
      return true;
    });

    for (HighlightInfo info : infos) {
      for (ReferenceImporter importer : ReferenceImporter.EP_NAME.getExtensionList()) {
        if (!importer.isAddUnambiguousImportsOnTheFlyEnabled(myFile)) {
          continue;
        }
        BooleanSupplier action = importer.computeAutoImportAtOffset(myEditor, myFile, info.getActualStartOffset(), false);
        if (action != null) {
          result.add(action);
        }
      }
    }
    autoImportActions.addAll(result);
  }

  @Override
  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().invokeLater(this::showImports);
  }

  private void showImports() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!UIUtil.hasFocus(myEditor.getContentComponent())) return;
    if (DumbService.isDumb(myProject) || !myFile.isValid()) return;
    if (myEditor.isDisposed() || myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid()) return;

    SlowOperations.allowSlowOperations(() -> {
      int caretOffset = myEditor.getCaretModel().getOffset();
      importUnambiguousImports();
      if (isImportHintEnabled()) {
        List<HighlightInfo> visibleHighlights = getVisibleHighlights(myVisibleRange, myProject, myEditor, hasDirtyTextRange);
        // sort by distance to the caret
        visibleHighlights.sort(Comparator.comparingInt(info -> Math.abs(info.getActualStartOffset() - caretOffset)));
        for (HighlightInfo visibleHighlight : visibleHighlights) {
          if (showAddImportHint(visibleHighlight)) {
            break;
          }
        }
      }
    });
  }

  private void importUnambiguousImports() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!mayAutoImportNow(myFile, myInContent)) return;
    for (BooleanSupplier autoImportAction : autoImportActions) {
      autoImportAction.getAsBoolean();
    }
  }

  public static boolean mayAutoImportNow(@NotNull PsiFile psiFile, boolean isInContent) {
    return isAddUnambiguousImportsOnTheFlyEnabled(psiFile) &&
           (ApplicationManager.getApplication().isUnitTestMode() || DaemonListeners.canChangeFileSilently(psiFile, isInContent)) &&
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
  private static List<HighlightInfo> getVisibleHighlights(@NotNull TextRange visibleRange,
                                                          @NotNull Project project,
                                                          @NotNull Editor editor,
                                                          boolean isDirty) {
    List<HighlightInfo> highlights = new ArrayList<>();
    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, null, visibleRange.getStartOffset(), visibleRange.getEndOffset(), info -> {
      //no changes after escape => suggest imports under caret only
      if (!isDirty && !info.containsOffset(offset, true)) {
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
    for (HintAction action : extractHints(info)) {
      if (action.isAvailable(myProject, myEditor, myFile) && action.showHint(myEditor)) {
        return true;
      }
    }
    return false;
  }

  private boolean isImportHintEnabled() {
    return DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled() &&
           DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile);
  }

  @NotNull
  static List<HintAction> extractHints(@NotNull HighlightInfo info) {
    List<HintAction> result = new ArrayList<>();
    info.findRegisteredQuickFix((descriptor, range) -> {
      ProgressManager.checkCanceled();
      IntentionAction action = descriptor.getAction();
      if (action instanceof HintAction) {
        result.add((HintAction)action);
      }
      return null;
    });
    return result;
  }


  @NotNull
  public static @NlsContexts.HintText String getMessage(boolean multiple, @NotNull String name) {
    String messageKey = multiple ? "import.popup.multiple" : "import.popup.text";
    String hintText = DaemonBundle.message(messageKey, name);
    hintText +=
      " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    return hintText;
  }
}
