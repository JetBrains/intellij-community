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
import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ignatov
 */
public class NewScratchFileAction extends DumbAwareAction {

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
    Language language = ObjectUtils.notNull(text.isEmpty() ? null : getLanguageFromCaret(project, editor, file), Language.ANY);
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

  public static VirtualFile openNewFile(@NotNull final Project project, @NotNull Language language, @NotNull String text) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");
    Language initialLanguage = language == Language.ANY ? StdLanguages.TEXT : language;
    final VirtualFile file = ScratchRootType.getInstance().createScratchFile(project, "scratch", initialLanguage, text);
    if (file != null) {
      FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
      if (language == Language.ANY && editors.length != 0 && editors[0] instanceof TextEditor) {
        final TextEditor textEditor = (TextEditor)editors[0];
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!textEditor.isValid()) return;
            PerFileMappings<Language> mappings = ScratchFileService.getInstance().getScratchesMapping();
            LRUPopupBuilder.forFileLanguages(project, file, mappings).showInBestPositionFor(textEditor.getEditor());
          }
        });
      }
    }
    return file;
  }


  public static class LanguageAction extends DumbAwareAction {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (project == null || file == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      ScratchFileService fileService = ScratchFileService.getInstance();
      if (!(fileService.getRootType(file) instanceof ScratchRootType)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      Language lang = fileService.getScratchesMapping().getMapping(file);
      if (lang == null) {
        lang = LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)file.getFileType()).getLanguage(), file, project);
      }
      e.getPresentation().setText("Change Language (" + lang.getDisplayName() +")...");
      e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (project == null || file == null) return;
      final PerFileMappings<Language> mapping = ScratchFileService.getInstance().getScratchesMapping();
      LRUPopupBuilder.forFileLanguages(project, file, mapping).showInBestPositionFor(e.getDataContext());
    }
  }

}