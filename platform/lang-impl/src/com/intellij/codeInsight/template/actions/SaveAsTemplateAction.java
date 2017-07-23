/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.actions;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.completion.OffsetsInFile;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

import java.util.*;

public class SaveAsTemplateAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(SaveAsTemplateAction.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = Objects.requireNonNull(CommonDataKeys.EDITOR.getData(dataContext));
    PsiFile file = Objects.requireNonNull(CommonDataKeys.PSI_FILE.getData(dataContext));

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(),
                                              editor.getSelectionModel().getSelectionEnd());
    int startOffset = selection.getStartOffset();

    final PsiElement[] psiElements = PsiTreeUtil.collectElements(file, new PsiElementFilter() {
      @Override
      public boolean isAccepted(PsiElement element) {
        return selection.contains(element.getTextRange()) && element.getReferences().length > 0;
      }
    });

    final Document document = EditorFactory.getInstance().createDocument(editor.getDocument().getText().
                                                                         substring(startOffset,
                                                                                   selection.getEndOffset()));
    final boolean isXml = file.getLanguage().is(StdLanguages.XML);
    final int offsetDelta = startOffset;
    new WriteCommandAction.Simple(project, (String)null) {
      @Override
      protected void run() throws Throwable {
        Map<RangeMarker, String> rangeToText = new HashMap<>();

        for (PsiElement element : psiElements) {
          for (PsiReference reference : element.getReferences()) {
            if (!(reference instanceof PsiQualifiedReference) || ((PsiQualifiedReference)reference).getQualifier() == null) {
              String canonicalText = reference.getCanonicalText();
              TextRange referenceRange = reference.getRangeInElement();
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
      }
    }.execute();

    TemplateImpl template = new TemplateImpl(TemplateListPanel.ABBREVIATION, document.getText().trim(), TemplateSettings.USER_GROUP_NAME);
    template.setToReformat(true);

    OffsetKey startKey = OffsetKey.create("pivot");
    OffsetsInFile offsets = new OffsetsInFile(file);
    offsets.getOffsets().addOffset(startKey, startOffset);
    OffsetsInFile copy = TemplateManagerImpl.copyWithDummyIdentifier(offsets,
                                                                     editor.getSelectionModel().getSelectionStart(),
                                                                     editor.getSelectionModel().getSelectionEnd(),
                                                                     CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);

    Set<TemplateContextType> applicable = TemplateManagerImpl.getApplicableContextTypes(copy.getFile(),
                                                                                        copy.getOffsets().getOffset(startKey));

    for (TemplateContextType contextType : TemplateManagerImpl.getAllContextTypes()) {
      template.getTemplateContext().setEnabled(contextType, applicable.contains(contextType));
    }

    final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
    SingleConfigurableEditor dialog = new SingleConfigurableEditor(project, configurable, DialogWrapper.IdeModalityType.MODELESS);
    new UiNotifyConnector.Once(dialog.getContentPane(), new Activatable.Adapter() {
      @Override
      public void showNotify() {
        configurable.getTemplateListPanel().addTemplate(template);
      }
    });
    dialog.setTitle(e.getPresentation().getText());
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    if (file == null || editor == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(editor.getSelectionModel().hasSelection());
    }
  }
}
