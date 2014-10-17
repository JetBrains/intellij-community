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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author ignatov
 */
public class NewScratchFileAction extends AnAction implements DumbAware {
  public static final int MAX_VISIBLE_SIZE = 20;

  public NewScratchFileAction() {
    super("New Scratch File...", null, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && Registry.is("ide.scratch.enabled"));
  }
  
  public static List<String> getLastUsedLanguagesIds(Project project) {
    String[] values = PropertiesComponent.getInstance(project).getValues(ScratchpadManager.class.getName());
    return values == null ? ContainerUtil.<String>emptyList() : ContainerUtil.list(values);
  }
  
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Language context = file != null ? file.getLanguage() : null;

    ListPopup popup = buildLanguagePopup(project, context, new Consumer<Language>() {
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
  static ListPopup buildLanguagePopup(@NotNull Project project, @Nullable Language context, final Consumer<Language> onChoosen) {
    List<Language> languages = LanguageUtil.getFileLanguages();
    final List<String> ids = ContainerUtil.newArrayList(getLastUsedLanguagesIds(project));
    if (context != null) {
      ids.add(context.getID());
    }
    if (ids.isEmpty()) {
      ids.add(StdLanguages.TEXT.getID());
    }
    
    ContainerUtil.sort(languages, new Comparator<Language>() {
      @Override
      public int compare(@NotNull Language o1, @NotNull Language o2) {
        int ind1 = ids.indexOf(o1.getID());
        int ind2 = ids.indexOf(o2.getID());
        if (ind1 == -1) ind1 = 666;
        if (ind2 == -1) ind2 = 666;
        return ind1 - ind2;
      }
    });
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
    step.setDefaultOptionIndex(0);

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
}