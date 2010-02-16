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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

  private final Map<Object, MyCheckpoint> myCheckpoints = new HashMap<Object, MyCheckpoint>();

  private static class MyCheckpoint {
    int myFixedLength = -1;
    int myFixedOffset;
  }

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
    return findApplicableTemplate(key) != null;
  }

  @Nullable
  public TemplateImpl findApplicableTemplate(@NotNull String key) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = TemplateManagerImpl.filterApplicableCandidates(myFile, myStartOffset, templates);
    return templates.size() > 0 ? templates.get(0) : null;
  }


  public boolean templateContainsVars(@NotNull String key, String... varNames) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = TemplateManagerImpl.filterApplicableCandidates(myFile, myStartOffset, templates);
    if (templates.size() == 0) {
      return false;
    }
    TemplateImpl template = templates.get(0);
    Set<String> varSet = new HashSet<String>();
    for (int i = 0; i < template.getVariableCount(); i++) {
      varSet.add(template.getVariableNameAt(i));
    }
    for (String varName : varNames) {
      if (!varSet.contains(varName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @param key
   * @param predefinedVarValues
   * @param listener            @return returns if template invokation is finished
   */
  public boolean startTemplate(@NotNull String key,
                               Map<String, String> predefinedVarValues,
                               @Nullable TemplateInvokationListener listener) {
    int caretOffset = myEditor.getCaretModel().getOffset();
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = TemplateManagerImpl.filterApplicableCandidates(myFile, caretOffset, templates);
    if (templates.size() > 0) {
      TemplateImpl template = templates.get(0);
      return startTemplate(template, predefinedVarValues, listener);
    }
    else if (listener != null) {
      listener.finished(false);
    }
    return true;
  }

  /**
   * @param template
   * @param predefinedVarValues
   * @param listener
   * @return returns if template invokation is finished
   */
  public boolean startTemplate(@NotNull Template template,
                               Map<String, String> predefinedVarValues,
                               @Nullable final TemplateInvokationListener listener) {
    final boolean[] templateEnded = new boolean[]{false};
    final boolean[] templateFinished = new boolean[]{false};
    myTemplateManager.startTemplate(myEditor, template, false, predefinedVarValues, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        final int lengthAfter = myEditor.getDocument().getCharsSequence().length();
        final CodeStyleManager style = CodeStyleManager.getInstance(myProject);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            style.reformatText(myFile, myStartOffset, myStartOffset + lengthAfter - myStartLength);
          }
        });
        if (brokenOff) return;
        templateFinished[0] = true;
        if (templateEnded[0] && listener != null) {
          listener.finished(true);
        }
      }
    });
    templateEnded[0] = true;
    if (templateFinished[0] && listener != null) {
      listener.finished(false);
    }
    return templateFinished[0];
  }

  public void fixStartOfTemplate(@NotNull Object key) {
    MyCheckpoint checkpoint = new MyCheckpoint();
    checkpoint.myFixedOffset = myEditor.getCaretModel().getOffset();
    checkpoint.myFixedLength = myEditor.getDocument().getTextLength();
    myCheckpoints.put(key, checkpoint);
  }

  public void gotoEndOfTemplate(@NotNull Object key) {
    myEditor.getCaretModel().moveToOffset(getEndOfTemplate(key));
  }

  public int getEndOfTemplate(@NotNull Object key) {
    MyCheckpoint checkpoint = myCheckpoints.get(key);
    if (checkpoint == null) {
      throw new IllegalArgumentException();
    }
    int length = myEditor.getDocument().getTextLength();
    return checkpoint.myFixedOffset + length - checkpoint.myFixedLength;
  }

  public int getStartOfTemplate(@NotNull Object key) {
    MyCheckpoint checkpoint = myCheckpoints.get(key);
    if (checkpoint == null) {
      throw new IllegalArgumentException();
    }
    return checkpoint.myFixedOffset;
  }

  private static List<TemplateImpl> getMatchingTemplates(@NotNull String templateKey) {
    TemplateSettings settings = TemplateSettings.getInstance();
    return settings.collectMatchingCandidates(templateKey, settings.getDefaultShortcutChar(), false);
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public int getOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  public PsiFile getFile() {
    return myFile;
  }
}
