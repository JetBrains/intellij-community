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

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

/**
 * Provides default implementation for {@link EditorTextFieldProvider} service and applies available
 * {@link EditorCustomization customizations} if necessary.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 3:21:03 PM
 */
public class EditorTextFieldProviderImpl implements EditorTextFieldProvider {

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

  @Override
  public EditorTextField getEditorField(@NotNull Language language,
                                        @NotNull final Project project,
                                        @NotNull final EditorCustomization.Feature... features)
  {
    return new LanguageTextField(language, project, "", false) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx ex = super.createEditor();
        ex.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        ex.setHorizontalScrollbarVisible(true);
        EditorSettings settings = ex.getSettings();
        settings.setAdditionalColumnsCount(3);
        settings.setVirtualSpace(false);
        EditorCustomization[] customizations = Extensions.getExtensions(EditorCustomization.EP_NAME, project);
        for (EditorCustomization.Feature feature : features) {
          for (EditorCustomization customization : customizations) {
            if (customization.getSupportedFeatures().contains(feature)) {
              customization.customize(ex, feature);
              break;
            }
          }
        }
        return ex;
      }

      @Override
      protected boolean isOneLineMode() {
        return false;
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
