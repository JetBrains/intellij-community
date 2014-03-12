/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author ignatov
 */
public class CreateScratchFileAction extends AnAction implements DumbAware {
  private static final Set<String> FORBIDDEN_LANGUAGES = ContainerUtil.newHashSet("<Generic>", "$XSLT");

  public CreateScratchFileAction() {
    super("Create Scratch File...", "New Scratch File", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    MyDialog dialog = new MyDialog(project);
    dialog.setResizable(false);
    if (dialog.showAndGet()) {
      Language language = dialog.getType();
      VirtualFile file = ScratchpadManager.getInstance(project).createScratchFile(language);
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
  }

  private static class MyDialog extends DialogWrapper {
    @Nullable private Project myProject;
    @NotNull  private JComboBox myComboBox;

    protected MyDialog(@Nullable Project project) {
      super(project);
      myProject = project;
      setTitle("Specify the Language");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      myComboBox = createCombo(getLanguages());
      panel.add(myComboBox, BorderLayout.CENTER);
      return panel;
    }

    @NotNull
    public Language getType() {
      return ((Language)myComboBox.getSelectedItem());
    }

    @NotNull
    private JComboBox createCombo(@NotNull List<Language> languages) {
      JComboBox jComboBox = new ComboBox(new CollectionComboBoxModel(languages));
      jComboBox.setRenderer(new ListCellRendererWrapper<Language>() {
        @Override
        public void customize(JList list, Language lang, int index, boolean selected, boolean hasFocus) {
          if (lang != null) {
            setText(lang.getDisplayName());
            LanguageFileType associatedLanguage = lang.getAssociatedFileType();
            if (associatedLanguage != null) setIcon(associatedLanguage.getIcon());
          }
        }
      });
      new ComboboxSpeedSearch(jComboBox) {
        @Override
        protected String getElementText(Object element) {
          return element instanceof Language ? ((Language)element).getDisplayName() : null;
        }
      };
      Language previous = myProject != null ? ScratchpadManager.getInstance(myProject).getLatestLanguage() : null;
      if (previous != null) {
        jComboBox.setSelectedItem(previous);
      }

      return jComboBox;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }
  }

  @NotNull
  private static List<Language> getLanguages() {
    Set<Language> result = ContainerUtil.newTreeSet(new Comparator<Language>() {
      @Override
      public int compare(Language l1, Language l2) {
        return l1.getDisplayName().compareTo(l2.getDisplayName());
      }
    });
    for (Language lang : Language.getRegisteredLanguages()) {
      if (!StringUtil.isEmpty(lang.getDisplayName())) result.add(lang);
      for (Language dialect : lang.getDialects()) result.add(dialect);
    }
    return ContainerUtil.filter(result, new Condition<Language>() {
      @Override
      public boolean value(Language lang) {
        String name = lang.getDisplayName();
        return !StringUtil.isEmpty(name) && !FORBIDDEN_LANGUAGES.contains(name);
      }
    });
  }
}