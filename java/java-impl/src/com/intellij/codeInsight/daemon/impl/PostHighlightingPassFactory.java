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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.MainHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.JspPsiUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author cdr
*/
public class PostHighlightingPassFactory extends AbstractProjectComponent implements MainHighlightingPassFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPassFactory");

  public PostHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar, HighlightVisitorImpl hvi) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL,}, null, true, Pass.POST_UPDATE_ALL);

    if (hvi == null) {
      HighlightVisitor[] extensions = Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject);
      LOG.error("com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl is unregistered; all available highlight visitors are: "+
                Arrays.asList(extensions));
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "PostHighlightingPassFactory";
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (textRange == null && PostHighlightingPass.isUpToDate(file)) return null;

    return create(file, editor.getDocument(), editor, new DefaultHighlightInfoProcessor());
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    return create(file, document, null, highlightInfoProcessor);
  }

  private PostHighlightingPass create(@NotNull PsiFile file,
                                      @NotNull Document document, Editor editor,
                                      @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getInspectionProfile();
    boolean importEnabled = isUnusedImportEnabled(unusedImportKey, file, profile);
    final UnusedDeclarationInspection myDeadCodeInspection = (UnusedDeclarationInspection)profile.getUnwrappedTool(UnusedDeclarationInspection.SHORT_NAME, file);
    HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspection.SHORT_NAME);
    final boolean myDeadCodeEnabled = profile.isToolEnabled(myDeadCodeKey, file);

    return new PostHighlightingPass(myProject, file, editor, document, highlightInfoProcessor, importEnabled, new Predicate<PsiElement>() {
      @Override
      public boolean apply(PsiElement member) {
        return !myDeadCodeEnabled || myDeadCodeInspection.isEntryPoint(member);
      }
    });
  }

  private static boolean isUnusedImportEnabled(HighlightDisplayKey unusedImportKey, @NotNull PsiFile file, InspectionProfile profile) {
    boolean unusedImportEnabled = profile.isToolEnabled(unusedImportKey, file);
    if (unusedImportEnabled && JspPsiUtil.isInJspFile(file)) {
      final JspFile jspFile = JspPsiUtil.getJspFile(file);
      if (jspFile != null) {
        unusedImportEnabled = !JspSpiUtil.isIncludedOrIncludesSomething(jspFile);
      }
    }
    return unusedImportEnabled;
  }
}
