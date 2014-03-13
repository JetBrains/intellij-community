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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author ignatov
 */
public class CreateScratchFileAction extends AnAction implements DumbAware {
  private static final Set<String> FORBIDDEN_LANGUAGES = ContainerUtil.newHashSet("<Generic>", "$XSLT", "JAVA_HOLDER_METHOD_TREE");

  public CreateScratchFileAction() {
    super("Create Scratch File...", "New Scratch File", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    List<Language> languages = getLanguages();
    BaseListPopupStep<Language> step =
      new BaseListPopupStep<Language>("Specify the language", languages) {
        @NotNull
        @Override
        public String getTextFor(Language value) {
          return value.getDisplayName();
        }

        @Override
        public String getIndexedString(Language value) {
          return value.getDisplayName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Language selectedValue, boolean finalChoice) {
          doAction(project, selectedValue);
          return null;
        }

        @Override
        public Icon getIconFor(Language language) {
          LanguageFileType associatedLanguage = language.getAssociatedFileType();
          return associatedLanguage != null ? associatedLanguage.getIcon() : null;
        }
      };

    Language previous = ScratchpadManager.getInstance(project).getLatestLanguage();
    final String previousName = previous != null ? previous.getDisplayName() : null;

    if (previousName != null) {
      int defaultOption = ContainerUtil.indexOf(languages, new Condition<Language>() {
        @Override
        public boolean value(Language module) {
          return module.getDisplayName().equals(previousName);
        }
      });
      if (defaultOption >= 0) {
        step.setDefaultOptionIndex(defaultOption);
      }
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    popup.setSize(new Dimension(190, 400));
    popup.showCenteredInCurrentWindow(project);
  }

  public static void doAction(@NotNull Project project, @NotNull Language language) {
    VirtualFile file = ScratchpadManager.getInstance(project).createScratchFile(language);
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
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