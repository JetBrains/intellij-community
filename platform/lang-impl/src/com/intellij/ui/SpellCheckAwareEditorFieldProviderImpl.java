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
package com.intellij.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since Aug 18, 2010 1:39:42 PM
 */
public class SpellCheckAwareEditorFieldProviderImpl implements SpellCheckAwareEditorFieldProvider {

  /**
   * Encapsulates sorting rule that defines what editor actions have precedence to non-editor actions. Current approach is that
   * we want to process text processing-oriented editor actions with higher priority than non-editor actions and all
   * other editor actions with lower priority.
   * <p/>
   * Rationale: there is at least one commit-specific action that is mapped to the editor action by default
   * (<code>'show commit messages history'</code> vs <code>'scroll to center'</code>). We want to process the former on target
   * short key triggering. Another example is that {@code 'Ctrl+Shift+Right/Left Arrow'} shortcut is bound to
   * <code>'expand/reduce selection by word'</code> editor action and <code>'change dialog width'</code> non-editor action
   * and we want to use the first one.
   */
  private static final Comparator<? super AnAction> ACTIONS_COMPARATOR = new Comparator<AnAction>() {
    @Override
    public int compare(AnAction o1, AnAction o2) {
      if (o1 instanceof EditorAction && o2 instanceof EditorAction) {
        return 0;
      }
      if (o1 instanceof TextComponentEditorAction) {
        return -1;
      }
      if (o2 instanceof TextComponentEditorAction) {
        return 1;
      }
      if (o1 instanceof EditorAction) {
        return 1;
      }
      if (o2 instanceof EditorAction) {
        return -1;
      }
      return 0;
    }
  };

  /**
   * Holds custom inspection profile wrapper.
   * <p/>
   * The general idea is that we want to use existing spell checking functionality within commit message all the time.
   * Unfortunately, we can't do that as-is because spell checking inspection may be disabled or specifically configured
   * (e.g. it fails to work with a 'plain text' if 'process code' option is not set).
   * <p/>
   * Hence, we define custom profile that is used during highlighting of commit area editor.
   */
  @Nullable
  private static final InspectionProfileWrapper INSPECTION_PROFILE_WRAPPER = initProvider();

  @SuppressWarnings("unchecked")
  @Nullable
  private static InspectionProfileWrapper initProvider() {
    // We don't want to add explicit dependency to 'spellchecker' module, hence, use reflection for instantiating
    // target inspection object. It's assumed that its default settings are just fine for processing commit dialog editor.
    // Please perform corresponding settings tuning if that assumption is broken at future.
    InspectionToolProvider provider;
    try {
      provider =
        (InspectionToolProvider)Class.forName("com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider").newInstance();
    }
    catch (Exception e) {
      return null;
    }

    final Map<String, LocalInspectionTool> tools = new HashMap<String, LocalInspectionTool>();
    Class<LocalInspectionTool>[] inspectionClasses = (Class<LocalInspectionTool>[])provider.getInspectionClasses();
    for (Class<LocalInspectionTool> inspectionClass : inspectionClasses) {
      try {
        LocalInspectionTool tool = inspectionClass.newInstance();
        tools.put(tool.getShortName(), tool);
      }
      catch (Throwable e) {
        return null;
      }
    }

    InspectionProfile profile = new InspectionProfileImpl("CommitMessage") {

      private final LocalInspectionTool[] myToolsArray = tools.values().toArray(new LocalInspectionTool[tools.size()]);

      @Override
      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
        return HighlightDisplayLevel.WARNING;
      }

      @Override
      public InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        return tools.get(shortName);
      }

      @NotNull
      @Override
      public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
        return myToolsArray;
      }

      @Override
      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return true;
      }
    };

    final List<LocalInspectionTool> toolsList = new ArrayList<LocalInspectionTool>(tools.values());

    return new InspectionProfileWrapper(profile) {
      @Override
      public List<LocalInspectionTool> getHighlightingLocalInspectionTools(PsiElement element) {
        return toolsList;
      }
    };
  }

  @Override
  public EditorTextField getEditorField(final Project project) {
    return new LanguageTextField(FileTypes.PLAIN_TEXT.getLanguage(), project, "") {
      @Override
      protected EditorEx createEditor() {
        final EditorEx ex = super.createEditor();
        ex.setOneLineMode(false);
        ex.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        ex.setHorizontalScrollbarVisible(true);
        EditorSettings settings = ex.getSettings();
        settings.setUseSoftWraps(true);
        settings.setAdditionalColumnsCount(0);
        if (INSPECTION_PROFILE_WRAPPER != null) {
          PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(getDocument());
          if (file != null) {
            file.putUserData(InspectionProfileWrapper.KEY, INSPECTION_PROFILE_WRAPPER);
          }
        }
        ex.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false);
        return ex;
      }

      @Override
      public Object getData(String dataId) {
        if (PlatformDataKeys.ACTIONS_SORTER.is(dataId)) {
          return ACTIONS_COMPARATOR;
        }
        return super.getData(dataId);
      }
    };
  }
}
