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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
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
public class NewScratchFileAction extends DumbAwareAction {
  private static final int MAX_VISIBLE_SIZE = 20;
  static Key<Boolean> IS_NEW_SCRATCH = Key.create("IS_NEW_SCRATCH");

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  public static boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && Registry.is("ide.scratch.enabled");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Editor editor = e.getData(CommonDataKeys.EDITOR);

    String text = StringUtil.notNullize(getSelectionText(editor));
    Language language = ObjectUtils.notNull(
      text.isEmpty() ? null : getLanguageFromCaret(project, editor, file), StdLanguages.TEXT);
    openNewFile(project, language, text);
  }

  @Nullable
  public String getSelectionText(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getSelectionModel().getSelectedText();
  }

  @Nullable
  public Language getLanguageFromCaret(@NotNull Project project,
                                       @Nullable Editor editor,
                                       @Nullable PsiFile psiFile) {
    if (editor == null || psiFile == null) return null;
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int offset = caret.getOffset();
    PsiElement element = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset);
    PsiFile file = element != null ? element.getContainingFile() : psiFile;
    return file.getLanguage();
  }

  public static VirtualFile openNewFile(@NotNull Project project, @NotNull Language language, @NotNull String text) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");
    storeLRULanguages(project, language);
    VirtualFile file = ScratchRootType.getInstance().createScratchFile(project, "scratch", language, text);
    if (file != null) {
      FileEditorManager.getInstance(project).openFile(file, true);
      file.putUserData(IS_NEW_SCRATCH, Boolean.TRUE);
    }
    return file;
  }

  private static void storeLRULanguages(@NotNull Project project, @NotNull Language language) {
    String[] values = PropertiesComponent.getInstance(project).getValues(getLRUStoreKey());
    List<String> lastUsed = ContainerUtil.newArrayListWithCapacity(5);
    lastUsed.add(language.getID());
    if (values != null) {
      for (String value : values) {
        if (!lastUsed.contains(value)) {
          lastUsed.add(value);
        }
        if (lastUsed.size() == 5) break;
      }
    }
    PropertiesComponent.getInstance(project).setValues(getLRUStoreKey(), ArrayUtil.toStringArray(lastUsed));
  }

  @NotNull
  private static List<String> restoreLRULanguages(@NotNull Project project) {
    String[] values = PropertiesComponent.getInstance(project).getValues(getLRUStoreKey());
    return values == null ? ContainerUtil.<String>emptyList() : ContainerUtil.list(values);
  }

  private static String getLRUStoreKey() {
    return NewScratchFileAction.class.getName();
  }

  @NotNull
  public static ListPopup buildLanguageSelectionPopup(@NotNull Project project, @NotNull String title,
                                                      @Nullable Language context, @NotNull final Consumer<Language> onChosen) {
    return buildLanguageSelectionPopup(project, title, context, LanguageUtil.getFileLanguages(), onChosen);
  }

  @NotNull
  public static ListPopup buildLanguageSelectionPopup(@NotNull Project project, @NotNull String title,
                                                      @Nullable Language context, @NotNull List<Language> languages,
                                                      @NotNull final Consumer<Language> onChosen) {
    final List<String> ids = ContainerUtil.newArrayList(restoreLRULanguages(project));
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
      new BaseListPopupStep<Language>(title, languages) {
        @NotNull
        @Override
        public String getTextFor(@NotNull Language value) {
          return value.getDisplayName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Language selectedValue, boolean finalChoice) {
          onChosen.consume(selectedValue);
          return null;
        }

        @Override
        public Icon getIconFor(@NotNull Language language) {
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
      //noinspection unchecked
      nameLen = Math.max(nameLen, step.getTextFor(v).length());
    }
    if (values.size() > MAX_VISIBLE_SIZE) {
      Dimension size = new JLabel(StringUtil.repeatSymbol('a', nameLen), EmptyIcon.ICON_16, SwingConstants.LEFT).getPreferredSize();
      size.width += 20;
      size.height *= MAX_VISIBLE_SIZE;
      popup.setSize(size);
    }
    return popup;
  }

}