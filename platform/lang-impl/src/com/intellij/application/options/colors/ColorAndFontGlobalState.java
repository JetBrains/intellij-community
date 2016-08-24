/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ColorAndFontGlobalState {
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);
  private HashMap<Language, Boolean> myLanguage2RainbowEnabled = new HashMap<>();
  private final ColorAndFontGlobalState myReferenceState;

  @Nullable
  @Contract("null -> !null")
  public Boolean isRainbowOn(@Nullable Language language) {
    assert myReferenceState != null;
    if (myLanguage2RainbowEnabled.containsKey(language)) {
      return myLanguage2RainbowEnabled.get(language);
    }
    Boolean rainbowOn = RainbowHighlighter.isRainbowEnabled(language);
    myReferenceState.myLanguage2RainbowEnabled.put(language, rainbowOn);
    myLanguage2RainbowEnabled.put(language, rainbowOn);
    return rainbowOn;
  }

  public boolean isRainbowOnWithInheritance(@Nullable Language language) {
    Boolean value = isRainbowOn(language);
    return value == null ? isRainbowOn(null) : value.booleanValue();
  }

  public void setRainbowOn(@Nullable Language language, @Nullable Boolean rainbowOn) {
    assert myReferenceState != null;
    myLanguage2RainbowEnabled.put(language, rainbowOn);
  }

  private ColorAndFontGlobalState(@SuppressWarnings("UnusedParameters") boolean unused) {
    myReferenceState = null;
  }

  public ColorAndFontGlobalState() {
    myReferenceState = new ColorAndFontGlobalState(true);
    copyFrom(myReferenceState); //be ready to global data extension
  }

  private void copyFrom(@NotNull ColorAndFontGlobalState state) {
    assert this != state;
    myLanguage2RainbowEnabled = new HashMap<>(state.myLanguage2RainbowEnabled);
  }

  public void apply() {
    for (Map.Entry<Language, Boolean> entry : myLanguage2RainbowEnabled.entrySet()) {
      RainbowHighlighter.setRainbowEnabled(entry.getKey(), entry.getValue());
    }
    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    for (Editor editor : allEditors) {
      final Project project = editor.getProject();
      if (project != null) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file != null) {
          DaemonCodeAnalyzer.getInstance(project).restart(file);
        }
      }
    }
    myReferenceState.copyFrom(this);
  }

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void stateChanged() {
    myDispatcher.getMulticaster().settingsChanged();
  }

  public boolean isModified() {
    return !myLanguage2RainbowEnabled.equals(myReferenceState.myLanguage2RainbowEnabled);
  }

  public boolean isModified(@Nullable Language language) {
    return myLanguage2RainbowEnabled.get(language) != myReferenceState.myLanguage2RainbowEnabled.get(language);
  }

  public void reset() {
    copyFrom(myReferenceState);
  }
}
