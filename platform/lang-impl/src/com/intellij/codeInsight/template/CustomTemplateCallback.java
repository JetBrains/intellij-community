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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class CustomTemplateCallback {
  private final TemplateManager myTemplateManager;
  private final Editor myEditor;
  private final PsiFile myFile;
  private int myStartOffset;
  private Project myProject;
  private RangeMarker myGlobalMarker;
  private RangeMarker myEndOffsetMarker;
  private final Map<Object, RangeMarker> myCheckpoints = new HashMap<Object, RangeMarker>();

  public CustomTemplateCallback(Editor editor, PsiFile file) {
    myEditor = editor;
    myFile = file;
    myProject = file.getProject();
    myTemplateManager = TemplateManagerImpl.getInstance(myProject);
    myStartOffset = myEditor.getCaretModel().getOffset();
    myGlobalMarker = myEditor.getDocument().createRangeMarker(myStartOffset, myStartOffset);
    myGlobalMarker.setGreedyToLeft(true);
    myGlobalMarker.setGreedyToRight(true);
  }

  public void fixEndOffset() {
    if (myEndOffsetMarker == null) {
      int offset = myEditor.getCaretModel().getOffset();
      myEndOffsetMarker = myEditor.getDocument().createRangeMarker(offset, offset);
    }
  }

  public boolean isLiveTemplateApplicable(@NotNull String key) {
    return findApplicableTemplate(key) != null;
  }

  @Nullable
  public TemplateImpl findApplicableTemplate(@NotNull String key) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = filterApplicableCandidates(templates);
    return templates.size() > 0 ? templates.get(0) : null;
  }

  private List<TemplateImpl> filterApplicableCandidates(Collection<TemplateImpl> candidates) {
    List<TemplateImpl> result = new ArrayList<TemplateImpl>();
    for (TemplateImpl candidate : candidates) {
      if (TemplateManagerImpl.isApplicable(myFile, myStartOffset, candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  public boolean templateContainsVars(@NotNull String key, String... varNames) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = filterApplicableCandidates(templates);
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
    //int caretOffset = myEditor.getCaretModel().getOffset();
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = filterApplicableCandidates(templates);
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
      public void templateFinished(Template template, final boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (brokenOff) {
              reformat();
            }
          }
        });
        if (brokenOff) return;
        templateFinished[0] = true;
        if (templateEnded[0] && listener != null) {
          listener.finished(true);
        }
      }

      @Override
      public void waitingForInput(Template template) {
        reformat();
      }
    });
    templateEnded[0] = true;
    if (templateFinished[0] && listener != null) {
      listener.finished(false);
    }
    return templateFinished[0];
  }

  private void reformat() {
    CodeStyleManager style = CodeStyleManager.getInstance(myProject);
    style.reformatText(myFile, myGlobalMarker.getStartOffset(), myGlobalMarker.getEndOffset());
  }

  public void fixStartOfTemplate(@NotNull Object key) {
    int offset = myEditor.getCaretModel().getOffset();
    RangeMarker marker = myEditor.getDocument().createRangeMarker(offset, offset);
    marker.setGreedyToLeft(true);
    marker.setGreedyToRight(true);
    myCheckpoints.put(key, marker);
  }

  public void gotoEndOfTemplate(@NotNull Object key) {
    myEditor.getCaretModel().moveToOffset(getEndOfTemplate(key));
  }

  public int getEndOfTemplate(@NotNull Object key) {
    RangeMarker marker = myCheckpoints.get(key);
    if (marker == null) {
      throw new IllegalArgumentException();
    }
    return marker.getEndOffset();
  }

  public int getStartOfTemplate(@NotNull Object key) {
    RangeMarker marker = myCheckpoints.get(key);
    if (marker == null) {
      throw new IllegalArgumentException();
    }
    return marker.getStartOffset();
  }

  public void gotoEndOffset() {
    if (myEndOffsetMarker != null) {
      myEditor.getCaretModel().moveToOffset(myEndOffsetMarker.getStartOffset());
    }
  }

  public void finish() {
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final CodeStyleManager style = CodeStyleManager.getInstance(myProject);
    if (myGlobalMarker != null) {
      style.reformatText(myFile, myGlobalMarker.getStartOffset(), myGlobalMarker.getEndOffset());
    }
    gotoEndOffset();
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

  public Project getProject() {
    return myProject;
  }
}
