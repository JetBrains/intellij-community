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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashMap;
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
  private int myStartOffset;
  private final Project myProject;
  private LiveTemplateBuilder.Marker myEndOffsetMarker;
  private final Map<Object, LiveTemplateBuilder.Marker> myCheckpoints = new HashMap<Object, LiveTemplateBuilder.Marker>();

  private FileType myFileType;

  private final LiveTemplateBuilder myBuilder = new LiveTemplateBuilder();
  private int myOffset = 0;

  public CustomTemplateCallback(Editor editor, PsiFile file) {
    myEditor = editor;
    myFile = file;
    myProject = file.getProject();
    myTemplateManager = TemplateManagerImpl.getInstance(myProject);
    fixInitialState();
  }

  @Nullable
  public PsiElement getContext() {
    int offset = myStartOffset;
    return myFile.findElementAt(offset > 0 ? offset - 1 : offset);
  }

  public void fixInitialState() {
    myStartOffset = myEditor.getCaretModel().getOffset();
  }

  public void fixEndOffset() {
    if (myEndOffsetMarker == null) {
      myEndOffsetMarker = myBuilder.createMarker(myOffset);
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

  /**
   * @param key
   * @param predefinedVarValues
   * @param listener            @return returns if template invokation is finished
   */
  public void startTemplate(@NotNull String key,
                            Map<String, String> predefinedVarValues) {
    List<TemplateImpl> templates = getMatchingTemplates(key);
    templates = filterApplicableCandidates(templates);
    if (templates.size() > 0) {
      TemplateImpl template = templates.get(0);
      startTemplate(template, predefinedVarValues);
    }
  }

  /**
   * @param template
   * @param predefinedVarValues
   * @param listener
   * @return returns if template invokation is finished
   */
  /*public boolean startTemplate(@NotNull Template template,
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
  }*/
  public void startTemplate(@NotNull TemplateImpl template,
                            Map<String, String> predefinedVarValues) {
    int offset = myBuilder.insertTemplate(myOffset, template, predefinedVarValues);
    moveToOffset(offset);
  }

  /*private void reformat() {
    CodeStyleManager style = CodeStyleManager.getInstance(myProject);
    style.reformatText(myFile, myGlobalMarker.getStartOffset(), myGlobalMarker.getEndOffset());
  }*/

  public void fixStartOfTemplate(@NotNull Object key) {
    LiveTemplateBuilder.Marker marker = myBuilder.createMarker(myOffset);
    myCheckpoints.put(key, marker);
  }

  public void gotoEndOfTemplate(@NotNull Object key) {
    moveToOffset(getEndOfTemplate(key));
  }

  public int getEndOfTemplate(@NotNull Object key) {
    LiveTemplateBuilder.Marker marker = myCheckpoints.get(key);
    if (marker == null) {
      throw new IllegalArgumentException();
    }
    return marker.getEndOffset();
  }

  public int getStartOfTemplate(@NotNull Object key) {
    LiveTemplateBuilder.Marker marker = myCheckpoints.get(key);
    if (marker == null) {
      throw new IllegalArgumentException();
    }
    return marker.getStartOffset();
  }

  public void gotoEndOffset() {
    if (myEndOffsetMarker != null) {
      moveToOffset(myEndOffsetMarker.getStartOffset());
    }
  }

  public void finish() {
    /*myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final CodeStyleManager style = CodeStyleManager.getInstance(myProject);
    if (myGlobalMarker != null) {
      style.reformatText(myFile, myGlobalMarker.getStartOffset(), myGlobalMarker.getEndOffset());
    }*/
    gotoEndOffset();
    if (myOffset < myBuilder.getText().length()) {
      myBuilder.insertVariableSegment(myOffset, TemplateImpl.END);
    }
    TemplateImpl template = myBuilder.buildTemplate();
    myTemplateManager.startTemplate(myEditor, template, false, myBuilder.getPredefinedValues(), null);
  }

  private static List<TemplateImpl> getMatchingTemplates(@NotNull String templateKey) {
    TemplateSettings settings = TemplateSettings.getInstance();
    return settings.collectMatchingCandidates(templateKey, settings.getDefaultShortcutChar(), false);
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

  public int getOffset() {
    return myOffset;
  }

  @NotNull
  public PsiFile parseCurrentText(FileType fileType) {
    return PsiFileFactory.getInstance(myProject)
      .createFileFromText("dummy.xml", fileType, myBuilder.getText(), LocalTimeCounter.currentTime(), false);
  }

  public Project getProject() {
    return myProject;
  }

  public void moveToOffset(int offset) {
    myOffset = offset;
  }

  public void insertString(int offset, String text) {
    myBuilder.insertText(offset, text);
  }
}
