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
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
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

  private final boolean myInInjectedFragment;

  private FileType myFileType;

  private LiveTemplateBuilder myBuilder = new LiveTemplateBuilder();
  private int myOffset = 0;

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
    return getContext(myFile, myStartOffset > 0 ? myStartOffset - 1 : myStartOffset);
  }

  public void fixInitialState() {
    myStartOffset = myEditor.getCaretModel().getOffset();
  }

  public void fixEndOffset() {
    if (myEndOffsetMarker == null) {
      myEndOffsetMarker = myBuilder.createMarker(myOffset);
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
  public void expandTemplate(@NotNull String key,
                             Map<String, String> predefinedVarValues) {
    List<TemplateImpl> templates = findApplicableTemplates(key);
    if (templates.size() > 0) {
      TemplateImpl template = templates.get(0);
      expandTemplate(template, predefinedVarValues);
    }
  }

  public void expandTemplate(@NotNull TemplateImpl template,
                             Map<String, String> predefinedVarValues) {
    int offset = myBuilder.insertTemplate(myOffset, template, predefinedVarValues);
    moveToOffset(offset);
  }

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

  public void startAllExpandedTemplates() {
    if (myBuilder.getText().length() == 0) {
      return;
    }
    gotoEndOffset();
    if (myOffset < myBuilder.getText().length()) {
      myBuilder.insertVariableSegment(myOffset, TemplateImpl.END);
    }
    TemplateImpl template = myBuilder.buildTemplate();
    template.setToReformat(!myInInjectedFragment);
    myTemplateManager.startTemplate(myEditor, template, false, myBuilder.getPredefinedValues(), null);
    myBuilder = new LiveTemplateBuilder();
    myEndOffsetMarker = null;
    myCheckpoints.clear();
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

  public boolean newLineBefore() {
    int i = myOffset - 1;
    CharSequence text = myBuilder.getText();
    while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
      if (text.charAt(i) == '\n') {
        return true;
      }
      i--;
    }
    return i < 0;
  }

  public boolean newLineAfter() {
    int i = myOffset;
    CharSequence text = myBuilder.getText();
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      if (text.charAt(i) == '\n') {
        return true;
      }
      i++;
    }
    return i == text.length();
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
}
