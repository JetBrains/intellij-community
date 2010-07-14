/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class CustomTemplateCallback {
  private final TemplateManager myTemplateManager;
  private final Editor myEditor;
  private final PsiFile myFile;
  private int myOffset;
  private final Project myProject;

  private final boolean myInInjectedFragment;

  private FileType myFileType;

  public CustomTemplateCallback(Editor editor, PsiFile file) {
    myProject = file.getProject();
    myTemplateManager = TemplateManagerImpl.getInstance(myProject);

    int caretOffset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(file, caretOffset);

    myFile = element != null ? element.getContainingFile() : file;

    myInInjectedFragment = InjectedLanguageManager.getInstance(myProject).isInjectedFragment(myFile);
    myEditor = myInInjectedFragment ? InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file) : editor;

    fixInitialState();
  }

  @NotNull
  public PsiElement getContext() {
    return getContext(myFile, myOffset > 0 ? myOffset - 1 : myOffset);
  }

  public void fixInitialState() {
    myOffset = myEditor.getCaretModel().getOffset();
  }

  @Nullable
  public TemplateImpl findApplicableTemplate(@NotNull String key) {
    List<TemplateImpl> templates = findApplicableTemplates(key);
    return templates.size() > 0 ? templates.get(0) : null;
  }

  @NotNull
  public List<TemplateImpl> findApplicableTemplates(String key) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = filterApplicableCandidates(templates);
    return templates;
  }

  private List<TemplateImpl> filterApplicableCandidates(Collection<TemplateImpl> candidates) {
    List<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateImpl candidate : candidates) {
      if (TemplateManagerImpl.isApplicable(myFile, myOffset, candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  public void startTemplate(Template template, Map<String, String> predefinedValues) {
    template.setToReformat(!myInInjectedFragment);
    myTemplateManager.startTemplate(myEditor, template, false, predefinedValues, null);
  }

  public boolean startTemplate() {
    Map<TemplateImpl, String> template2Argument =
      ((TemplateManagerImpl)myTemplateManager).findMatchingTemplates(myFile, myEditor, null, TemplateSettings.getInstance());
    return ((TemplateManagerImpl)myTemplateManager).startNonCustomTemplates(template2Argument, myEditor, null);
  }

  private static List<TemplateImpl> getMatchingTemplates(@NotNull String templateKey) {
    TemplateSettings settings = TemplateSettings.getInstance();
    List<TemplateImpl> candidates = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : settings.getTemplates(templateKey)) {
      if (!template.isDeactivated() && !template.isSelectionTemplate()) {
        candidates.add(template);
      }
    }
    return candidates;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public FileType getFileType() {
    if (myFileType == null) {
      myFileType = myFile.getFileType();
    }
    return myFileType;
  }

  public Project getProject() {
    return myProject;
  }

  public void deleteTemplateKey(String key) {
    int caretAt = myEditor.getCaretModel().getOffset();
    myEditor.getDocument().deleteString(caretAt - key.length(), caretAt);
  }

  @NotNull
  public static PsiElement getContext(@NotNull PsiFile file, int offset) {
    PsiElement element = null;
    if (!InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      element = InjectedLanguageUtil.findInjectedElementNoCommit(file, offset);
    }
    if (element == null) {
      element = file.findElementAt(offset > 0 ? offset - 1 : offset);
      if (element == null) {
        element = file;
      }
    }
    return element;
  }

  public boolean isInInjectedFragment() {
    return myInInjectedFragment;
  }
}
