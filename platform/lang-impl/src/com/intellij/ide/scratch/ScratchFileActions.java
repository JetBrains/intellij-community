// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.*;
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

  private static int ourCurrentBuffer = 0;

  private static int nextBufferIndex() {
    ourCurrentBuffer = (ourCurrentBuffer % Registry.intValue("ide.scratch.buffers")) + 1;
    return ourCurrentBuffer;
  }


  public static class NewFileAction extends DumbAwareAction {
    private static final Icon ICON = LayeredIcon.create(AllIcons.FileTypes.Text, AllIcons.Actions.Scratch);

    private static final String ACTION_ID = "NewScratchFile";

    private static final String SMALLER_IDE_CONTAINER_GROUP = "PlatformOpenProjectGroup";

    private final String myActionText;

    public NewFileAction() {
      getTemplatePresentation().setIcon(ICON);
      // A hacky way for customizing text in IDEs without File->New-> submenu
      myActionText = (isIdeWithoutNewSubmenu() ? "New " : "") + ActionsBundle.actionText(ACTION_ID);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      getTemplatePresentation().setText(myActionText);

      Project project = e.getProject();
      String place = e.getPlace();
      boolean enabled = project != null && (
        e.isFromActionToolbar() ||
        ActionPlaces.isMainMenuOrActionSearch(place) ||
        ActionPlaces.isPopupPlace(place) && e.getData(LangDataKeys.IDE_VIEW) != null);

      e.getPresentation().setEnabledAndVisible(enabled);
      updatePresentationTextAndIcon(e, e.getPresentation());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;

      ScratchFileCreationHelper.Context context = createContext(e, project);
      Consumer<Language> consumer = l -> {
        context.language = l;
        ScratchFileCreationHelper.EXTENSION.forLanguage(context.language).prepareText(
          project, context, DataContext.EMPTY_CONTEXT);
        doCreateNewScratch(project, context);
      };
      if (context.language != null) {
        consumer.consume(context.language);
      }
      else {
        LRUPopupBuilder.forFileLanguages(project, "New " + ActionsBundle.actionText(ACTION_ID), null, consumer).showCenteredInCurrentWindow(project);
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
      if (PlatformUtils.isRider()) return true;
      final AnAction group = ActionManager.getInstance().getActionOrStub(SMALLER_IDE_CONTAINER_GROUP);
      return group instanceof DefaultActionGroup && ContainerUtil.find(((DefaultActionGroup)group).getChildActionsOrStubs(), action ->
        action == this || (action instanceof ActionStub && ((ActionStub)action).getId().equals(ACTION_ID))) != null;
    }
  }

  public static class NewBufferAction extends DumbAwareAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = e.getProject() != null && Registry.intValue("ide.scratch.buffers") > 0;
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      ScratchFileCreationHelper.Context context = createContext(e, project);
      context.filePrefix = "buffer";
      context.createOption = ScratchFileService.Option.create_if_missing;
      context.fileCounter = ScratchFileActions::nextBufferIndex;
      if (context.language == null) context.language = StdLanguages.TEXT;
      doCreateNewScratch(project, context);
    }
  }

  @NotNull
  static ScratchFileCreationHelper.Context createContext(@NotNull AnActionEvent e, @NotNull Project project) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (file == null && editor != null) {
      // see data provider in com.intellij.diff.tools.holders.TextEditorHolder
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }

    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.text = StringUtil.notNullize(getSelectionText(editor));
    if (!context.text.isEmpty()) {
      context.language = getLanguageFromCaret(project, editor, file);
      checkLanguageAndTryToFixText(project, context, e.getDataContext());
    }
    else {
      context.text = StringUtil.notNullize(e.getData(PlatformDataKeys.PREDEFINED_TEXT));
    }
    context.ideView = e.getData(LangDataKeys.IDE_VIEW);
    return context;
  }

  static void doCreateNewScratch(@NotNull Project project, @NotNull ScratchFileCreationHelper.Context context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");
    Language language = ObjectUtils.notNull(context.language);
    if (context.fileExtension == null) {
      LanguageFileType fileType = language.getAssociatedFileType();
      context.fileExtension = fileType == null ? "" : fileType.getDefaultExtension();
    }
    ScratchFileCreationHelper.EXTENSION.forLanguage(language).beforeCreate(project, context);

    VirtualFile dir = context.ideView != null ? PsiUtilCore.getVirtualFile(ArrayUtil.getFirstElement(context.ideView.getDirectories())) : null;
    RootType rootType = dir == null ? null : ScratchFileService.getInstance().getRootType(dir);
    String relativePath = rootType != ScratchRootType.getInstance() ? "" :
                          FileUtil.getRelativePath(ScratchFileService.getInstance().getRootPath(rootType), dir.getPath(), '/');

    String fileName = (StringUtil.isEmpty(relativePath) ? "" : relativePath + "/") +
                      PathUtil.makeFileName(ObjectUtils.notNull(context.filePrefix, "scratch") +
                                            (context.fileCounter != null ? context.fileCounter.create() : ""),
                                            context.fileExtension);
    VirtualFile file = ScratchRootType.getInstance().createScratchFile(
      project, fileName, language, context.text, context.createOption);
    if (file == null) return;

    PsiNavigationSupport.getInstance().createNavigatable(project, file, context.caretOffset).navigate(true);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (context.ideView != null && psiFile != null) {
      context.ideView.selectElement(psiFile);
    }
  }

  private static void checkLanguageAndTryToFixText(@NotNull Project project,
                                                   @NotNull ScratchFileCreationHelper.Context context,
                                                   @NotNull DataContext dataContext) {
    if (context.language == null) return;
    ScratchFileCreationHelper handler = ScratchFileCreationHelper.EXTENSION.forLanguage(context.language);
    if (handler.prepareText(project, context, dataContext)) return;

    PsiFile psiFile = ScratchFileCreationHelper.parseHeader(project, context.language, context.text);
    PsiErrorElement firstError = SyntaxTraverser.psiTraverser(psiFile).traverse().filter(PsiErrorElement.class).first();
    // heuristics: first error must not be right under the file PSI
    // otherwise let the user choose the language manually
    if (firstError != null && firstError.getParent() == psiFile) {
      context.language = null;
    }
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
    Language language = file.getLanguage();
    if (language == StdLanguages.TEXT && file.getFileType() instanceof InternalFileType) {
      return StdLanguages.XML;
    }
    return language;
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
      actionPerformedImpl(e, project, "Change " + getLanguageTerm(), files);
    }

    @NotNull
    protected Condition<VirtualFile> fileFilter(Project project) {
      return file -> ScratchRootType.getInstance().containsFile(file);
    }

    @NotNull
    protected Function<VirtualFile, Language> fileLanguage(@NotNull Project project) {
      return new Function<VirtualFile, Language>() {
        ScratchFileService fileService = ScratchFileService.getInstance();

        @Override
        public Language fun(VirtualFile file) {
          Language lang = fileService.getScratchesMapping().getMapping(file);
          return lang != null ? lang : LanguageUtil.getLanguageForPsi(project, file);
        }
      };
    }

    protected void actionPerformedImpl(@NotNull AnActionEvent e,
                                       @NotNull Project project,
                                       @NotNull String title,
                                       @NotNull JBIterable<VirtualFile> files) {
      ScratchFileService fileService = ScratchFileService.getInstance();
      PerFileMappings<Language> mapping = fileService.getScratchesMapping();
      LRUPopupBuilder.forFileLanguages(project, title, files, mapping)
                     .showInBestPositionFor(e.getDataContext());
    }
  }
}