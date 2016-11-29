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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.JavaCommentContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * @author Denis Zhdanov
 * @since 4/27/11 3:07 PM
 */
public class ShortenToStaticImportProcessor implements TemplateOptionalProcessor {

  private static final List<StaticImporter> IMPORTERS = asList(new SingleMemberStaticImporter(), new OnDemandStaticImporter());
  
  @Override
  public void processText(Project project, Template template, Document document, RangeMarker templateRange, Editor editor) {
    if (!template.getValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE)) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitDocument(document);
    final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file == null) {
       return;
    }

    List<Pair<PsiElement, StaticImporter>> staticImportTargets = new ArrayList<>();
    for (
      PsiElement element = PsiUtilCore.getElementAtOffset(file, templateRange.getStartOffset());
      element != null && element.getTextRange().getStartOffset() < templateRange.getEndOffset();
      element = PsiTreeUtil.nextLeaf(element))
    {
      for (StaticImporter importer : IMPORTERS) {
        if (importer.canPerform(element)) {
          staticImportTargets.add(Pair.create(element, importer));
          break;
        }
      }
    }

    Collections.reverse(staticImportTargets);
    for (Pair<PsiElement, StaticImporter> pair : staticImportTargets) {
      if (pair.first.isValid()) {
        pair.second.perform(project, file, editor, pair.first);
      }
    }
  }

  @Nls
  @Override
  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.use.static.import");
  }

  @Override
  public boolean isEnabled(Template template) {
    return template.getValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE);
  }

  @Override
  public void setEnabled(Template template, boolean value) {
    template.setValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE, value);
  }

  @Override
  public boolean isVisible(@NotNull Template template, @NotNull TemplateContext context) {
    for (TemplateContextType contextType : TemplateContextType.EP_NAME.getExtensions()) {
      if (!context.isEnabled(contextType)) continue;
      if (contextType instanceof JavaCodeContextType || contextType instanceof JavaCommentContextType) {
        return true;
      }
    }
    return false;
  }
  
  private interface StaticImporter {
    boolean canPerform(@NotNull PsiElement element);
    void perform(Project project, PsiFile file, Editor editor, PsiElement element);
  }
  
  private static class SingleMemberStaticImporter implements StaticImporter {
    @Override
    public boolean canPerform(@NotNull PsiElement element) {
      return AddSingleMemberStaticImportAction.getStaticImportClass(element) != null;
    }

    @Override
    public void perform(Project project, PsiFile file, Editor editor, PsiElement element) {
      AddSingleMemberStaticImportAction.invoke(file, element);
    }
  }
  
  private static class OnDemandStaticImporter implements StaticImporter {
    @Override
    public boolean canPerform(@NotNull PsiElement element) {
      return AddOnDemandStaticImportAction.getClassToPerformStaticImport(element) != null;
    }

    @Override
    public void perform(Project project, PsiFile file, Editor editor, PsiElement element) {
      AddOnDemandStaticImportAction.invoke(project, file, editor, element);
    }
  }
}
