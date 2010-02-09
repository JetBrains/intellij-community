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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class CustomTemplateCallback {
  private final TemplateManager myTemplateManager;
  private final Editor myEditor;
  private final PsiFile myFile;
  private int myStartOffset;
  private int myStartLength;
  private Project myProject;

  public CustomTemplateCallback(Editor editor, PsiFile file) {
    myEditor = editor;
    myFile = file;
    myProject = file.getProject();
    myTemplateManager = TemplateManagerImpl.getInstance(myProject);
  }

  public void fixInitialEditorState() {
    myStartOffset = myEditor.getCaretModel().getOffset();
    myStartLength = myEditor.getDocument().getCharsSequence().length();
  }

  public boolean isLiveTemplateApplicable(@NotNull String key) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = TemplateManagerImpl.filterApplicableCandidates(myFile, myStartOffset, templates);
    return templates.size() > 0;
  }

  /**
   * @param key
   * @param listener
   * @return returns if template invokation is finished
   */
  public boolean startTemplate(@NotNull String key, @Nullable TemplateInvokationListener listener) {
    int caretOffset = myEditor.getCaretModel().getOffset();
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = TemplateManagerImpl.filterApplicableCandidates(myFile, caretOffset, templates);
    if (templates.size() == 1) {
      TemplateImpl template = templates.get(0);
      return startTemplate(template, listener);
    }
    else if (listener != null) {
      listener.finished(false, false);
    }
    return true;
  }

  /**
   * @param template
   * @param listener
   * @return returns if template invokation is finished
   */
  public boolean startTemplate(@NotNull Template template, @Nullable final TemplateInvokationListener listener) {
    final boolean[] templateEnded = new boolean[]{false};
    final boolean[] templateFinished = new boolean[]{false};
    myTemplateManager.startTemplate(myEditor, template, new TemplateEditingAdapter() {

      @Override
      public void templateExpanded(Template template) {
        int lengthAfter = myEditor.getDocument().getCharsSequence().length();
        CodeStyleManager style = CodeStyleManager.getInstance(myProject);
        style.reformatText(myFile, myStartOffset, myStartOffset + lengthAfter - myStartLength);
      }

      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (brokenOff) return;
        templateFinished[0] = true;
        if (templateEnded[0] && listener != null) {
          listener.finished(true, true);
        }
      }
    }, false);
    templateEnded[0] = true;
    if (templateFinished[0] && listener != null) {
      listener.finished(false, true);
    }
    return templateFinished[0];
  }

  private static List<TemplateImpl> getMatchingTemplates(@NotNull String templateKey) {
    TemplateSettings settings = TemplateSettings.getInstance();
    return settings.collectMatchingCandidates(templateKey, settings.getDefaultShortcutChar(), false);
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }
}
