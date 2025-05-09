// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class CopyPasteReferenceProcessor<TRef extends PsiElement> extends CopyPastePostProcessor<ReferenceTransferableData>
  implements ReferenceCopyPasteProcessor {
  private static final Logger LOG = Logger.getInstance(CopyPasteReferenceProcessor.class);

  @Override
  public @NotNull List<ReferenceTransferableData> collectTransferableData(@NotNull PsiFile file,
                                                                          @NotNull Editor editor,
                                                                          int @NotNull [] startOffsets,
                                                                          int @NotNull [] endOffsets) {
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) {
      return Collections.emptyList();
    }

    if (!(file instanceof PsiClassOwner)) {
      return Collections.emptyList();
    }

    ArrayList<ReferenceData> array = new ArrayList<>();
    int refOffset = 0; // this is an offset delta for conversion from absolute offset to an offset inside clipboard contents
    for (int j = 0; j < startOffsets.length; j++) {
      refOffset += startOffsets[j];
      for (PsiElement element : CollectHighlightsUtil.getElementsInRange(file, startOffsets[j], endOffsets[j])) {
        addReferenceData(file, refOffset, element, array);
      }
      refOffset -= endOffsets[j] + 1; // 1 accounts for line break inserted between contents corresponding to different carets
    }

    if (array.isEmpty()) {
      return Collections.emptyList();
    }
    
    return Collections.singletonList(new ReferenceTransferableData(array.toArray(new ReferenceData[0])));
  }

  protected abstract void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to);

  @Override
  public @NotNull List<ReferenceTransferableData> extractTransferableData(@NotNull Transferable content) {
    ReferenceTransferableData referenceData = null;
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
      try {
        DataFlavor flavor = ReferenceData.getDataFlavor();
        if (flavor != null) {
          referenceData = (ReferenceTransferableData)content.getTransferData(flavor);
        }
      }
      catch (UnsupportedFlavorException | IOException ignored) {
      }
    }

    if (referenceData != null) { // copy to prevent changing of original by convertLineSeparators
      return Collections.singletonList(referenceData.clone());
    }

    return Collections.emptyList();
  }

  @Override
  public void processTransferableData(@NotNull Project project,
                                      @NotNull Editor editor,
                                      @NotNull RangeMarker bounds,
                                      int caretOffset,
                                      @NotNull Ref<? super Boolean> indented, @NotNull List<? extends ReferenceTransferableData> values) {
    if (DumbService.getInstance(project).isDumb()) {
      return;
    }
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (!(file instanceof PsiClassOwner)) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    assert values.size() == 1;
    ReferenceData[] referenceData = values.get(0).getData();
    TRef[] refs;
    refs = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(
        () -> findReferencesToRestore(file, bounds, referenceData)
      ), JavaBundle.message("progress.title.searching.references"), true, project);
    if (refs == null) return;
    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
      askReferencesToRestore(project, refs, referenceData);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    Consumer<ProgressIndicator> consumer = indicator -> {
      Set<String> imported = new TreeSet<>();
      createAndApplyRestoreReferencesFixWithModCommand(referenceData, refs, imported, editor, file);
      if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.YES && !imported.isEmpty()) {
        Integer size = imported.size();
        String notificationText = JavaBundle.message("copy.paste.reference.notification", size);
        app.invokeLater(
          () -> showHint(editor, notificationText, e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              reviewImports(project, file, imported);
            }
          }), ModalityState.nonModal(), __ -> editor.isDisposed());
      }
    };
    consumer.accept(null);
  }

  private void createAndApplyRestoreReferencesFixWithModCommand(ReferenceData[] data,
                                                                TRef[] refs,
                                                                @NotNull Set<String> imported,
                                                                @NotNull Editor editor,
                                                                @NotNull PsiFile file) {
    if (refs.length == 0) return;
    Optional<TRef> ref1 = Arrays.stream(refs).filter(Objects::nonNull).findAny();
    if (ref1.isEmpty()) return;
    ActionContext context = ActionContext.from(editor, file).withOffset(ref1.get().getTextRange().getStartOffset());
    ModCommandExecutor.executeInteractively(context,
                                            JavaBundle.message("progress.title.searching.references"),
                                            editor,
                                            () -> {
                                              return ModCommand.psiUpdate(context.file(), (e, updater) -> {
                                                List<TRef> copies = ContainerUtil.map(refs, el -> updater.getWritable(el));
                                                restoreReferences(data, copies, imported, updater);
                                              });
                                            });
  }

  protected abstract void removeImports(@NotNull PsiFile file, @NotNull Set<String> imports);

  private void reviewImports(@NotNull Project project, @NotNull PsiFile file, @NotNull Set<String> importedClasses) {
    RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, ArrayUtil.toObjectArray(importedClasses), false);
    dialog.setTitle(JavaBundle.message("dialog.import.on.paste.title3"));
    dialog.setExplanation(JavaBundle.message("dialog.paste.on.import.text3"));
    if (dialog.showAndGet()) {
      Object[] selectedElements = dialog.getSelectedElements();
      if (selectedElements.length > 0) {
        WriteCommandAction.runWriteCommandAction(project, "", null, () ->
          removeImports(file, Arrays.stream(selectedElements).map(o -> (String)o).collect(Collectors.toSet())));
      }
    }
  }

  protected static void addReferenceData(@NotNull PsiElement element,
                                         @NotNull List<? super ReferenceData> array,
                                         int startOffset,
                                         @NotNull String qClassName,
                                         @Nullable String staticMemberName) {
    TextRange range = element.getTextRange();
    array.add(
      new ReferenceData(
        range.getStartOffset() - startOffset,
        range.getEndOffset() - startOffset,
        qClassName, staticMemberName));
  }

  protected abstract TRef @NotNull [] findReferencesToRestore(@NotNull PsiFile file,
                                                              @NotNull RangeMarker bounds,
                                                              ReferenceData @NotNull [] referenceData);

  protected static PsiElement resolveReferenceIgnoreOverriding(@NotNull PsiPolyVariantReference reference) {
    PsiElement referent = reference.resolve();
    if (referent == null) {
      ResolveResult[] results = reference.multiResolve(true);
      if (results.length > 0) {
        referent = results[0].getElement();
      }
    }
    return referent;
  }

  protected abstract void restoreReferences(ReferenceData @NotNull [] referenceData,
                                            List<TRef> refs,
                                            @NotNull Set<? super String> imported);

  protected void restoreReferences(ReferenceData @NotNull [] referenceData,
                                   List<TRef> refs,
                                   @NotNull Set<? super String> imported,
                                   @NotNull ModPsiUpdater updater) {
    restoreReferences(referenceData, refs, imported);
  }

  private static void askReferencesToRestore(@NotNull Project project, PsiElement @NotNull [] refs,
                                             ReferenceData @NotNull [] referenceData) {
    PsiManager manager = PsiManager.getInstance(project);

    ThrowableComputable<Pair<ArrayList<Object>, Object[]>, RuntimeException> computable = () -> {
      ArrayList<Object> array = new ArrayList<>();
      Object[] refObjects = new Object[refs.length];

      for (int i = 0; i < referenceData.length; i++) {
        PsiElement ref = refs[i];
        if (ref != null) {
          LOG.assertTrue(ref.isValid());
          ReferenceData data = referenceData[i];
          PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(data.qClassName, ref.getResolveScope());
          if (refClass == null) continue;

          Object refObject = refClass;
          if (data.staticMemberName != null) {
            //Show static members as Strings
            refObject = refClass.getQualifiedName() + "." + data.staticMemberName;
          }
          refObjects[i] = refObject;

          if (!array.contains(refObject)) {
            array.add(refObject);
          }
        }
      }
      return new Pair<>(array, refObjects);
    };
    Pair<ArrayList<Object>, Object[]> context;
    context = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(
        computable
      ), JavaBundle.message("progress.title.searching.references"), true, project);
    if (context == null) return;
    ArrayList<Object> array = context.getFirst();
    Object[] refObjects = context.getSecond();
    if (array.isEmpty()) return;

    Object[] selectedObjects = ArrayUtil.toObjectArray(array);
    Arrays.sort(selectedObjects, (o1, o2) -> getFQName(o1).compareToIgnoreCase(getFQName(o2)));

    RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, selectedObjects);
    dialog.show();
    selectedObjects = dialog.getSelectedElements();

    for (int i = 0; i < referenceData.length; i++) {
      PsiElement ref = refs[i];
      if (ref != null) {
        PsiUtilCore.ensureValid(ref);
        Object refObject = refObjects[i];
        boolean found = false;
        for (Object selected : selectedObjects) {
          if (Comparing.equal(refObject, selected)) {
            found = true;
            break;
          }
        }
        if (!found) {
          refs[i] = null;
        }
      }
    }
  }

  private static void showHint(@NotNull Editor editor,
                               @NotNull @NlsContexts.HintText String info,
                               @Nullable HyperlinkListener hyperlinkListener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(info, hyperlinkListener, null, null));

    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE;
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER, flags, 0, false);
  }

  private static String getFQName(@NotNull Object element) {
    return element instanceof PsiClass ? ((PsiClass)element).getQualifiedName() : (String)element;
  }
}
