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

  public CustomTemplateCallback(Editor editor, PsiFile file, boolean wrapping) {
    myProject = file.getProject();
    myTemplateManager = TemplateManager.getInstance(myProject);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    int offset = getOffset(wrapping, editor);
    PsiElement element = InjectedLanguageUtil.findInjectedElementNoCommit(file, offset);
    myFile = element != null ? element.getContainingFile() : file;

    myInInjectedFragment = InjectedLanguageManager.getInstance(myProject).isInjectedFragment(myFile);
    myEditor = myInInjectedFragment ? InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file, offset) : editor;

    fixInitialState(wrapping);
  }

  @NotNull
  public PsiElement getContext() {
    return getContext(myFile, myOffset);
  }

  public void fixInitialState(boolean wrapping) {
    myOffset = getOffset(wrapping, myEditor);
  }

  private static int getOffset(boolean wrapping, Editor editor) {
    if (wrapping) {
      return editor.getSelectionModel().getSelectionStart();
    }
    else {
      int caretOffset = editor.getCaretModel().getOffset();
      return caretOffset > 0 ? caretOffset - 1 : 0;
    }
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
      /*TemplateContext context = candidate.getTemplateContext();
      for (TemplateContextType contextType : contextTypes) {
        if (context.isEnabled(contextType)) {
          result.add(candidate);
          break;
        }
      }*/
    }
    return result;
  }

  public void startTemplate(Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
    if(myInInjectedFragment) {
      template.setToReformat(false);
    }
    myTemplateManager.startTemplate(myEditor, template, false, predefinedValues, listener);
  }

  public void startTemplate() {
    Map<TemplateImpl, String> template2Argument =
      ((TemplateManagerImpl)myTemplateManager).findMatchingTemplates(myFile, myEditor, null, TemplateSettings.getInstance());
    Runnable runnable = ((TemplateManagerImpl)myTemplateManager).startNonCustomTemplates(template2Argument, myEditor, null);
    if (runnable != null) {
      runnable.run();
    }
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
      element = file.findElementAt(offset);
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
