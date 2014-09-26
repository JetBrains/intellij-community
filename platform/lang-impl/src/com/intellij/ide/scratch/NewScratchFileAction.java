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

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.EmptyIcon;
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
public class NewScratchFileAction extends AnAction implements DumbAware {
  public static final int MAX_VISIBLE_SIZE = 20;

  public NewScratchFileAction() {
    super("New Scratch Pad...", null, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && Registry.is("ide.scratch.enabled"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    Language previous = ScratchpadManager.getInstance(project).getLatestLanguage();
    ListPopup popup = buildLanguagePopup(previous, new Consumer<Language>() {
      @Override
      public void consume(Language language) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");
        VirtualFile file = ScratchpadManager.getInstance(project).createScratchFile(language);
        FileEditorManager.getInstance(project).openFile(file, true);
      }
    });
    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  static ListPopup buildLanguagePopup(@Nullable Language previous, final Consumer<Language> onChoosen) {
    List<Language> languages = getLanguages();
    BaseListPopupStep<Language> step =
      new BaseListPopupStep<Language>("Choose Language", languages) {
        @NotNull
        @Override
        public String getTextFor(Language value) {
          return value.getDisplayName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Language selectedValue, boolean finalChoice) {
          onChoosen.consume(selectedValue);
          return null;
        }

        @Override
        public Icon getIconFor(Language language) {
          LanguageFileType associatedLanguage = language.getAssociatedFileType();
          return associatedLanguage != null ? associatedLanguage.getIcon() : null;
        }
      };
    step.setDefaultOptionIndex(Math.max(0, languages.indexOf(ObjectUtils.chooseNotNull(previous, StdLanguages.TEXT))));

    return tweakSizeToPreferred(JBPopupFactory.getInstance().createListPopup(step));
  }

  @NotNull
  private static ListPopup tweakSizeToPreferred(@NotNull ListPopup popup) {
    int nameLen = 0;
    ListPopupStep step = popup.getListStep();
    List values = step.getValues();
    for (Object v : values) {
      nameLen = Math.max(nameLen, step.getTextFor(v).length());
    }
    if (values.size() > MAX_VISIBLE_SIZE) {
      Dimension size = new JLabel(StringUtil.repeatSymbol('a', nameLen), EmptyIcon.ICON_16, SwingConstants.LEFT).getMinimumSize();
      size.height *= MAX_VISIBLE_SIZE;
      popup.setSize(size);
    }
    return popup;
  }


  @NotNull
  private static List<Language> getLanguages() {
    Set<Language> result = ContainerUtilRt.newTreeSet(new Comparator<Language>() {
      @Override
      public int compare(@NotNull Language l1, @NotNull Language l2) {
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
        if (lang instanceof DependentLanguage || lang instanceof InjectableLanguage) return false;
        LanguageFileType type = lang.getAssociatedFileType();
        if (type == null) return false;
        String name = lang.getDisplayName();
        if (StringUtil.startsWith(name, "<") || StringUtil.startsWith(name, "[") || StringUtil.isEmpty(name) || StringUtil.equalsIgnoreCase(name, "SQL")) return false;
        return !StringUtil.isEmpty(type.getDefaultExtension());
      }
    });
  }
}