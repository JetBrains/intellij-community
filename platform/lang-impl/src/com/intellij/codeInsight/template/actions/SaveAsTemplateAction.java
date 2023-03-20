// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.actions;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.completion.OffsetsInFile;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

public class SaveAsTemplateAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(SaveAsTemplateAction.class);

  @NotNull
  @VisibleForTesting
  public static String suggestTemplateText(@NotNull Editor editor, @Nullable PsiFile file, @NotNull Project project) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    PsiElement[] psiElements = PsiTreeUtil.collectElements(file, element -> selection.contains(element.getTextRange()));

    Document document = EditorFactory.getInstance().createDocument(selection.substring(editor.getDocument().getText()));
    boolean isXml = file != null && file.getLanguage().is(StdLanguages.XML);
    int offsetDelta = editor.getSelectionModel().getSelectionStart();
    WriteCommandAction.writeCommandAction(project).withName(null).run(() -> {
      Map<RangeMarker, String> rangeToText = new HashMap<>();

      for (PsiElement element : psiElements) {
        for (PsiReference reference : element.getReferences()) {
          if (!(reference instanceof PsiQualifiedReference) || ((PsiQualifiedReference)reference).getQualifier() == null) {
            String canonicalText = reference.getCanonicalText();
            TextRange referenceRange = reference.getRangeInElement();
            if (referenceRange.isEmpty()) continue;

            final TextRange elementTextRange = element.getTextRange();
            LOG.assertTrue(elementTextRange != null, elementTextRange);
            final TextRange range = elementTextRange.cutOut(referenceRange).shiftRight(-offsetDelta);
            final String oldText = document.getText(range);
            // workaround for Java references: canonicalText contains generics, and we need to cut them off because otherwise
            // they will be duplicated
            int pos = canonicalText.indexOf('<');
            if (pos > 0 && !oldText.contains("<")) {
              canonicalText = canonicalText.substring(0, pos);
            }
            if (isXml) { //strip namespace prefixes
              pos = canonicalText.lastIndexOf(':');
              if (pos >= 0 && pos < canonicalText.length() - 1 && !oldText.contains(":")) {
                canonicalText = canonicalText.substring(pos + 1);
              }
            }
            if (!canonicalText.equals(oldText)) {
              rangeToText.put(document.createRangeMarker(range), canonicalText);
            }
          }
        }
      }

      List<RangeMarker> markers = new ArrayList<>();
      for (RangeMarker m1 : rangeToText.keySet()) {
        boolean nested = false;
        for (RangeMarker m2 : rangeToText.keySet()) {
          if (m1 != m2 && m2.getStartOffset() <= m1.getStartOffset() && m1.getEndOffset() <= m2.getEndOffset()) {
            nested = true;
            break;
          }
        }

        if (!nested) {
          markers.add(m1);
        }
      }

      for (RangeMarker marker : markers) {
        final String value = rangeToText.get(marker);
        document.replaceString(marker.getStartOffset(), marker.getEndOffset(), value);
      }
    });

    return document.getText().trim();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = Objects.requireNonNull(CommonDataKeys.EDITOR.getData(dataContext));
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    Project project = Objects.requireNonNull(e.getProject());
    String templateText = suggestTemplateText(editor, file, project);
    TemplateImpl template = new TemplateImpl(TemplateListPanel.ABBREVIATION, templateText, TemplateSettings.USER_GROUP_NAME);
    template.setToReformat(true);

    if (file != null) {
      OffsetKey startKey = OffsetKey.create("pivot");
      OffsetsInFile offsets = new OffsetsInFile(file);
      offsets.getOffsets().addOffset(startKey, editor.getSelectionModel().getSelectionStart());
      OffsetsInFile copy = TemplateManagerImpl.copyWithDummyIdentifier(offsets,
                                                                       editor.getSelectionModel().getSelectionStart(),
                                                                       editor.getSelectionModel().getSelectionEnd(),
                                                                       CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);

      Set<TemplateContextType> applicable = TemplateManagerImpl.getApplicableContextTypes(
        TemplateActionContext.expanding(copy.getFile(), copy.getOffsets().getOffset(startKey)));

      for (TemplateContextType contextType : TemplateManagerImpl.getAllContextTypes()) {
        template.getTemplateContext().setEnabled(contextType, applicable.contains(contextType));
      }
    }

    final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
    SingleConfigurableEditor dialog = new SingleConfigurableEditor(project, configurable, DialogWrapper.IdeModalityType.MODELESS);
    UiNotifyConnector.Once.installOn(dialog.getContentPane(), new Activatable() {
      @Override
      public void showNotify() {
        configurable.getTemplateListPanel().addTemplate(template);
      }
    });
    dialog.setTitle(e.getPresentation().getText());
    dialog.show();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    e.getPresentation().setEnabled(e.getProject() != null && editor != null && editor.getSelectionModel().hasSelection() && !EditorUtil.contextMenuInvokedOutsideOfSelection(e));
  }
}
