// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.NewActionGroup;
import com.intellij.ide.actions.RecentLocationsAction;
import com.intellij.ide.scratch.ScratchImplUtil.LanguageItem;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.util.Conditions.not;

public final class ScratchFileActions {
  private static int ourCurrentBuffer = 0;

  private static int nextBufferIndex() {
    ourCurrentBuffer = (ourCurrentBuffer % Registry.intValue("ide.scratch.buffers")) + 1;
    return ourCurrentBuffer;
  }


  public static class NewFileAction extends DumbAwareAction implements UpdateInBackground {
    private static final Icon ICON = LayeredIcon.create(AllIcons.FileTypes.Text, AllIcons.Actions.Scratch);

    private static final String ACTION_ID = "NewScratchFile";

    private final NotNullLazyValue<@Nls String> myActionText = NotNullLazyValue.lazy(() -> {
      return NewActionGroup.isActionInNewPopupMenu(this)
             ? ActionsBundle.actionText(ACTION_ID)
             : ActionsBundle.message("action.NewScratchFile.text.with.new");
    });

    public NewFileAction() {
      getTemplatePresentation().setIcon(ICON);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      getTemplatePresentation().setText(myActionText.getValue());

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
      Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);

      // selection from the current editor
      ScratchFileCreationHelper.Context context = createContext(e);
      LanguageItem selectionItem =
        context.language != null ? LanguageItem.fromLanguage(context.language) :
        context.fileExtension != null ? new LanguageItem(
          null, FileTypeManager.getInstance().getFileTypeByExtension(context.fileExtension), context.fileExtension) :
        StringUtil.isNotEmpty(context.text) ? new LanguageItem(
          null, PlainTextFileType.INSTANCE, PlainTextFileType.INSTANCE.getDefaultExtension()) : null;

      // extract text from the focused component, e.g. a tree or a list
      ScratchImplUtil.TextExtractor textExtractor = selectionItem == null ? ScratchImplUtil.getTextExtractor(component) : null;
      LanguageItem extractItem =
        textExtractor != null && StringUtil.isEmpty(context.text) &&
        !EditorUtil.isRealFileEditor(e.getData(CommonDataKeys.EDITOR)) ?
        new LanguageItem(null, PlainTextFileType.INSTANCE, PlainTextFileType.INSTANCE.getDefaultExtension()) : null;

      Consumer<LanguageItem> consumer = o -> {
        context.language = o.language;
        context.fileExtension = o.fileExtension;
        if (o == extractItem) {
          context.text = StringUtil.notNullize(textExtractor.extractText());
          context.caretOffset = 0;
        }
        else if (o != selectionItem) {
          context.text = "";
          context.caretOffset = 0;
        }
        doCreateNewScratch(project, context);
      };
      if (selectionItem != null && ApplicationManager.getApplication().isUnitTestMode()) {
        consumer.consume(selectionItem);
        return;
      }
      LRUPopupBuilder<LanguageItem> builder = ScratchImplUtil.buildLanguagesPopup(
        project, ActionsBundle.message("action.NewScratchFile.text.with.new"));
      if (selectionItem != null) {
        String displayName = LangBundle.message("scratch.file.action.new.from.selection", selectionItem.fileType.getDisplayName());
        builder.withExtraTopValue(selectionItem, displayName, EmptyIcon.ICON_16);
      }
      else if (extractItem != null) {
        String displayName = LangBundle.message("scratch.file.action.new.from.ui");
        if (textExtractor.hasSelection()) {
          builder.withExtraTopValue(extractItem, displayName, EmptyIcon.ICON_16);
        }
        else {
          builder.withExtraMiddleValue(extractItem, displayName, EmptyIcon.ICON_16);
        }
      }
      builder
        .onChosen(consumer)
        .buildPopup()
        .showCenteredInCurrentWindow(project);
    }

    private void updatePresentationTextAndIcon(@NotNull AnActionEvent e, @NotNull Presentation presentation) {
      presentation.setText(myActionText.getValue());
      presentation.setIcon(ICON);
      if (ActionPlaces.MAIN_MENU.equals(e.getPlace()) && !NewActionGroup.isActionInNewPopupMenu(this)) {
        presentation.setIcon(null);
      }
    }
  }

  public static class NewBufferAction extends DumbAwareAction implements UpdateInBackground {

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = e.getProject() != null && Registry.intValue("ide.scratch.buffers") > 0;
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
      context.filePrefix = "buffer";
      context.createOption = ScratchFileService.Option.create_if_missing;
      context.fileCounter = ScratchFileActions::nextBufferIndex;
      context.language = PlainTextLanguage.INSTANCE;
      doCreateNewScratch(project, context);
    }
  }

  static @NotNull ScratchFileCreationHelper.Context createContext(@NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getProject());
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (file == null && editor != null) {
      // see data provider in com.intellij.diff.tools.holders.TextEditorHolder
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    return createContext(project, file, editor, e.getDataContext());
  }

  static @NotNull ScratchFileCreationHelper.Context createContext(@NotNull Project project,
                                                                  @Nullable PsiFile file,
                                                                  @Nullable Editor editor,
                                                                  @NotNull DataContext dataContext) {
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.text = StringUtil.notNullize(getSelectionText(editor));
    if (StringUtil.isNotEmpty(context.text)) {
      initLanguageFromCaret(project, editor, file, context, dataContext);
    }
    context.ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    return context;
  }

  static @Nullable PsiFile doCreateNewScratch(@NotNull Project project, @NotNull ScratchFileCreationHelper.Context context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("scratch");
    if (context.fileExtension == null && context.language != null) {
      LanguageFileType fileType = context.language.getAssociatedFileType();
      if (fileType != null) {
        context.fileExtension = ScratchImplUtil.getFileTypeExtensions(fileType, true, FileTypeManager.getInstance()).first();
      }
    }
    if (context.language != null) {
      ScratchFileCreationHelper helper = ScratchFileCreationHelper.EXTENSION.forLanguage(context.language);
      if (StringUtil.isEmpty(context.text)) {
        helper.prepareText(project, context, DataContext.EMPTY_CONTEXT);
      }
      helper.beforeCreate(project, context);
    }

    VirtualFile dir = context.ideView != null ? PsiUtilCore.getVirtualFile(ArrayUtil.getFirstElement(context.ideView.getDirectories())) : null;
    RootType rootType = dir == null ? null : ScratchFileService.findRootType(dir);
    String relativePath = rootType != ScratchRootType.getInstance() ? "" :
                          FileUtil.getRelativePath(ScratchFileService.getInstance().getRootPath(rootType), dir.getPath(), '/');

    String fileName = (StringUtil.isEmpty(relativePath) ? "" : relativePath + "/") +
                      PathUtil.makeFileName(ObjectUtils.notNull(context.filePrefix, "scratch") +
                                            (context.fileCounter != null ? context.fileCounter.create() : ""),
                                            context.fileExtension);
    VirtualFile file = ScratchRootType.getInstance().createScratchFile(
      project, fileName, context.language, context.text, context.createOption);
    if (file == null) return null;

    Navigatable navigatable = PsiNavigationSupport.getInstance().createNavigatable(project, file, context.caretOffset);
    navigatable.navigate(!LaterInvocator.isInModalContextForProject(project));
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (context.ideView != null && psiFile != null) {
      context.ideView.selectElement(psiFile);
    }
    return psiFile;
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

  static @Nullable String getSelectionText(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getSelectionModel().getSelectedText(true);
  }

  private static void initLanguageFromCaret(@NotNull Project project,
                                            @Nullable Editor editor,
                                            @Nullable PsiFile psiFile,
                                            @NotNull ScratchFileCreationHelper.Context context,
                                            @NotNull DataContext dataContext) {
    if (editor == null || psiFile == null) return;
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int offset = caret.getOffset();
    PsiElement element = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset);
    PsiFile file = element != null ? element.getContainingFile() : psiFile;
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = ScratchImplUtil.getFileTypeFromName(file.getName(), fileTypeManager);
    context.language = fileType instanceof LanguageFileType ? file.getLanguage() : null;
    if (fileType != null) {
      context.fileExtension = ScratchImplUtil.getFileTypeExtensions(fileType, true, fileTypeManager).first();
    }
    if (context.language == PlainTextLanguage.INSTANCE && file.getFileType() instanceof InternalFileType) {
      context.language = StdLanguages.XML;
    }
    checkLanguageAndTryToFixText(project, context, dataContext);
  }

  public static class ChangeLanguageAction extends DumbAwareAction implements UpdateInBackground {
    @Override
    public void update(@NotNull AnActionEvent e) {
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
      FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      Set<String> languages = files
        .filter(isScratch)
        .map(file -> {
          // language substitution is not invoked for AbstractFileType (non-LanguageFileType)
          Language language = LanguageUtil.getFileLanguage(file) == null ? null : fileLanguage(project, file);
          if (language != null) return language.getDisplayName();
          return fileTypeManager.getFileTypeByFileName(file.getName()).getDisplayName();
        })
        .toSet();
      String langName = languages.size() == 1 ? languages.iterator().next() :
                        LangBundle.message("scratch.file.actions.0.different.languages.number", languages.size());
      e.getPresentation().setText(getChangeLanguageActionName(langName));
      e.getPresentation().setEnabledAndVisible(true);
    }

    protected @NotNull @Nls String getChangeLanguageActionName(@NotNull String languageName) {
      return LangBundle.message("scratch.file.action.change.language.action", languageName);
    }

    protected @NotNull @Nls String getChangeLanguageTitle() {
      return LangBundle.message("scratch.file.action.change.language.title");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).filter(fileFilter(project));
      if (files.isEmpty()) return;
      actionPerformedImpl(e, project, getChangeLanguageTitle(), files);
    }

    protected @NotNull Condition<VirtualFile> fileFilter(@NotNull Project project) {
      return file -> !file.isDirectory() && ScratchRootType.getInstance().containsFile(file);
    }

    protected @Nullable Language fileLanguage(@NotNull Project project,
                                             @NotNull VirtualFile file) {
      Language lang = ScratchFileService.getInstance().getScratchesMapping().getMapping(file);
      return lang != null ? lang : LanguageUtil.getLanguageForPsi(project, file);
    }

    protected void actionPerformedImpl(@NotNull AnActionEvent e,
                                       @NotNull Project project,
                                       @NotNull @NlsContexts.PopupTitle String title,
                                       @NotNull JBIterable<? extends VirtualFile> files) {
      ScratchFileService fileService = ScratchFileService.getInstance();
      PerFileMappings<Language> mapping = fileService.getScratchesMapping();
      VirtualFile[] filesCopy = VfsUtilCore.toVirtualFileArray(JBIterable.from((Iterable<? extends VirtualFile>)files).toList());
      Arrays.sort(filesCopy, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), !o1.isCaseSensitive()));
      ScratchImplUtil.buildLanguagesPopup(project, title)
        .onChosen(item -> {
          try {
            WriteCommandAction.writeCommandAction(project).withName(LangBundle.message("command.name.change.language")).run(
              () -> ScratchImplUtil.changeLanguageWithUndo(project, item, filesCopy, mapping));
          }
          catch (UnexpectedUndoException e1) {
            ExceptionUtil.rethrowUnchecked(e1);
          }
        })
        .buildPopup()
        .showCenteredInCurrentWindow(project);
    }
  }

  public static class ShowFilesPopupAction extends DumbAwareAction implements UpdateInBackground {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      RecentLocationsAction.showPopup(
        project, false, LangBundle.message("scratch.file.popup.title"),
        LangBundle.message("scratch.file.popup.changed.title"),
        LangBundle.message("scratch.file.popup.title.empty.text"),
        changed -> getPlaces(project, changed),
        toRemove -> removePlaces(project, toRemove));
    }

    private static @NotNull List<IdeDocumentHistoryImpl.PlaceInfo> getPlaces(@NotNull Project project, boolean changed) {
      String path = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(path);
      if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) return Collections.emptyList();
      Condition<? super VirtualFile> condition;
      if (!changed) {
        condition = Conditions.alwaysTrue();
      }
      else {
        Set<VirtualFile> files = JBIterable.from(IdeDocumentHistory.getInstance(project).getChangePlaces())
          .map(o -> o.getFile()).toSet();
        condition = files::contains;
      }
      List<IdeDocumentHistoryImpl.PlaceInfo> result = new ArrayList<>();
      VfsUtilCore.visitChildrenRecursively(rootDir, new VirtualFileVisitor<>(VirtualFileVisitor.SKIP_ROOT) {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (file.isDirectory() || !file.isValid() || !condition.value(file)) return true;
          Document document = FileDocumentManager.getInstance().getDocument(file);
          if (document == null) return true;
          RangeMarker caret = document.createRangeMarker(0, 0);
          result.add(new IdeDocumentHistoryImpl.PlaceInfo(file, new TextEditorState(), "text-editor", null, caret));
          return result.size() < 1000;
        }
      });
      Collections.sort(result, Comparator.comparing(o -> o.getFile().getName(), NaturalComparator.INSTANCE));
      return result;
    }

    private static void removePlaces(@NotNull Project project, @NotNull List<IdeDocumentHistoryImpl.PlaceInfo> toRemove) {
      PsiManager psiManager = PsiManager.getInstance(project);
      List<PsiFile> files = ContainerUtil.mapNotNull(toRemove, o -> psiManager.findFile(o.getFile()));
      DeleteHandler.deletePsiElement(files.toArray(PsiElement.EMPTY_ARRAY), project, false);
    }
  }

  public static class ExportToScratchAction extends DumbAwareAction implements UpdateInBackground {
    {
      setEnabledInModalContext(true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      Component c = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      ScratchImplUtil.TextExtractor extractor = ScratchImplUtil.getTextExtractor(c);
      boolean isFileEditor = EditorUtil.isRealFileEditor(e.getData(CommonDataKeys.EDITOR));
      e.getPresentation().setEnabled(project != null && extractor != null && !isFileEditor);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      ScratchImplUtil.TextExtractor extractor = ScratchImplUtil.getTextExtractor(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
      String text = extractor == null ? null : extractor.extractText();
      if (text == null) return;
      ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
      context.text = text;
      context.fileExtension = PlainTextFileType.INSTANCE.getDefaultExtension();
      doCreateNewScratch(project, context);
    }
  }
}