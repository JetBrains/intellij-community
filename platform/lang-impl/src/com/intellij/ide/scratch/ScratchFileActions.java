/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static com.intellij.openapi.util.Conditions.not;
import static com.intellij.openapi.util.Conditions.notNull;

/**
 * @author ignatov
 */
public class ScratchFileActions {

  public static class NewFileAction extends DumbAwareAction {
    private static final Icon ICON = LayeredIcon.create(AllIcons.FileTypes.Text, AllIcons.Actions.Scratch);

    private static final String ACTION_ID = "NewScratchFile";

    private static final String SMALLER_IDE_CONTAINER_GROUP = "PlatformOpenProjectGroup";

    private final String myActionText;

    public NewFileAction() {
      final Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setIcon(ICON);
      // A hacky way for customizing text in IDEs without File->New-> submenu
      myActionText = (isIdeWithoutNewSubmenu() ? "New " : "") + ActionsBundle.actionText(ACTION_ID);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      getTemplatePresentation().setText(myActionText);

      String place = e.getPlace();
      boolean enabled = e.getProject() != null
                        && Registry.is("ide.scratch.enabled")
                        && (ActionPlaces.isToolbarPlace(e.getPlace()) || ActionPlaces.isMainMenuOrActionSearch(place));
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(enabled);

      updatePresentationTextAndIcon(e, presentation);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null) return;

      PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
      Editor editor = e.getData(CommonDataKeys.EDITOR);

      String eventText = getSelectionText(editor);
      if (eventText == null) {
        eventText = e.getData(PlatformDataKeys.PREDEFINED_TEXT);
      }
      final String text = StringUtil.notNullize(eventText);
      Language language = text.isEmpty() ? null : detectLanguageFromSelection(project, editor, file, text);
      Consumer<Language> consumer = language1 -> doCreateNewScratch(project, false, language1, text);
      if (language != null) {
        consumer.consume(language);
      }
      else {
        LRUPopupBuilder.forFileLanguages(project, null, consumer).showCenteredInCurrentWindow(project);
      }
    }

    private void updatePresentationTextAndIcon(@NotNull AnActionEvent e, @NotNull Presentation presentation) {
      presentation.setText(myActionText);
      presentation.setIcon(ICON);
      if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
        if (isIdeWithoutNewSubmenu()) {
          presentation.setIcon(null);
        }
      }
    }

    private boolean isIdeWithoutNewSubmenu() {
      final AnAction group = ActionManager.getInstance().getActionOrStub(SMALLER_IDE_CONTAINER_GROUP);
      return group instanceof DefaultActionGroup && ContainerUtil.find(((DefaultActionGroup)group).getChildActionsOrStubs(), action ->
        action == this || (action instanceof ActionStub && ((ActionStub)action).getId().equals(ACTION_ID))) != null;
    }
  }

  @Nullable
  private static Language detectLanguageFromSelection(Project project, Editor editor, PsiFile file, String text) {
    Language language = getLanguageFromCaret(project, editor, file);
    FileType fileType = LanguageUtil.getLanguageFileType(language);
    if (fileType == null) return null;

    CharSequence fileSnippet = text.subSequence(0, Math.min(text.length(), 10 * 1024));
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(file.getProject());
    PsiFile psiFile = fileFactory.createFileFromText("a." + fileType.getDefaultExtension(), language, fileSnippet);
    PsiErrorElement firstError = SyntaxTraverser.psiTraverser(psiFile).traverse().filter(PsiErrorElement.class).first();
    // heuristics: first error must not be right under the file PSI
    return firstError == null || firstError.getParent() != psiFile ? language : null;
  }

  public static class NewBufferAction extends DumbAwareAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = e.getProject() != null && Registry.is("ide.scratch.enabled") && Registry.intValue("ide.scratch.buffers") > 0;
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;

      PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
      Editor editor = e.getData(CommonDataKeys.EDITOR);

      String text = StringUtil.notNullize(getSelectionText(editor));
      Language language = text.isEmpty() ? null : detectLanguageFromSelection(project, editor, file, text);

      doCreateNewScratch(project, true, ObjectUtils.notNull(language, StdLanguages.TEXT), text);
    }
  }

  static void doCreateNewScratch(@NotNull Project project, boolean buffer, @NotNull Language language, @NotNull String text) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");

    LanguageFileType fileType = language.getAssociatedFileType();
    String ext = buffer || fileType == null? "" : fileType.getDefaultExtension();
    String fileName = buffer ? "buffer" + nextBufferIndex() : "scratch";
    ScratchFileService.Option option = buffer ? ScratchFileService.Option.create_if_missing : ScratchFileService.Option.create_new_always;
    VirtualFile f = ScratchRootType.getInstance().createScratchFile(project, PathUtil.makeFileName(fileName, ext), language, text, option);
    if (f != null) {
      FileEditorManager.getInstance(project).openFile(f, true);
    }
  }

  private static int ourCurrentBuffer = 0;
  private static int nextBufferIndex() {
    ourCurrentBuffer = (ourCurrentBuffer % Registry.intValue("ide.scratch.buffers")) + 1;
    return ourCurrentBuffer;
  }

  @Nullable
  static String getSelectionText(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getSelectionModel().getSelectedText();
  }

  @Nullable
  static Language getLanguageFromCaret(@NotNull Project project,
                                       @Nullable Editor editor,
                                       @Nullable PsiFile psiFile) {
    if (editor == null || psiFile == null) return null;
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int offset = caret.getOffset();
    PsiElement element = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset);
    PsiFile file = element != null ? element.getContainingFile() : psiFile;
    return file.getLanguage();
  }

  public static class LanguageAction extends DumbAwareAction {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
      if (project == null || files.isEmpty()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      Condition<VirtualFile> isScratch = fileFilter(project);
      if (!files.filter(not(isScratch)).isEmpty()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      Set<Language> languages = files.filter(isScratch).transform(fileLanguage(project)).filter(notNull()).
        addAllTo(ContainerUtil.newLinkedHashSet());
      String langName = languages.size() == 1 ? languages.iterator().next().getDisplayName() : languages.size() + " different";
      e.getPresentation().setText(String.format("Change %s (%s)...", getLanguageTerm(), langName));
      e.getPresentation().setEnabledAndVisible(true);
    }

    @NotNull
    protected String getLanguageTerm() {
      return "Language";
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).
        filter(fileFilter(project));
      if (project == null || files.isEmpty()) return;
      actionPerformedImpl(e, project, files);
    }

    @NotNull
    protected Condition<VirtualFile> fileFilter(Project project) {
      return file -> ScratchRootType.getInstance().containsFile(file);
    }

    @NotNull
    protected Function<VirtualFile, Language> fileLanguage(final Project project) {
      return new Function<VirtualFile, Language>() {
        ScratchFileService fileService = ScratchFileService.getInstance();

        @Override
        public Language fun(VirtualFile file) {
          Language lang = fileService.getScratchesMapping().getMapping(file);
          return lang != null ? lang : LanguageUtil.getLanguageForPsi(project, file);
        }
      };
    }

    protected void actionPerformedImpl(AnActionEvent e, Project project, JBIterable<VirtualFile> files) {
      ScratchFileService fileService = ScratchFileService.getInstance();
      PerFileMappings<Language> mapping = fileService.getScratchesMapping();
      LRUPopupBuilder.forFileLanguages(project, files, mapping).showInBestPositionFor(e.getDataContext());
    }
  }
}