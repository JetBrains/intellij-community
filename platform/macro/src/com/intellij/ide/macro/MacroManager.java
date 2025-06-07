// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.DataManager;
import com.intellij.ide.macro.Macro.ExecutionCancelledException;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@Service
public final class MacroManager {
  private final List<Macro> predefinedMacros = new ArrayList<>();

  private static final Pattern MACRO_PATTERN = Pattern.compile("\\$.+\\$");

  public static final DataKey<MacroPathConverter> PATH_CONVERTER_KEY = DataKey.create("MacroPathConverter");
  public static final DataKey<Path> CONTEXT_PATH = DataKey.create("MacroContextPath");

  public static MacroManager getInstance() {
    return ApplicationManager.getApplication().getService(MacroManager.class);
  }

  private MacroManager() {
    registerMacro(new SourcepathMacro());
    registerMacro(new FileDirMacro());
    registerMacro(new FileDirNameMacro());
    registerMacro(new FileParentDirMacro());
    registerMacro(new FileDirPathFromParentMacro());
    registerMacro(new FileExtMacro());
    registerMacro(new FileNameMacro());
    registerMacro(new FileNameWithoutExtension());
    registerMacro(new FileNameWithoutAllExtensions());
    registerMacro(new FilePathMacro());
    registerMacro(new UnixSeparatorsMacro());
    registerMacro(new FileEncodingMacro());
    registerMacro(new FileDirRelativeToProjectRootMacro());
    registerMacro(new FilePathRelativeToProjectRootMacro());
    registerMacro(new FileDirRelativeToSourcepathMacro());
    registerMacro(new FilePathRelativeToSourcepathMacro());
    registerMacro(new JdkPathMacro());
    registerMacro(new PromptMacro());
    registerMacro(new PasswordMacro());
    registerMacro(new FilePromptMacro());
    registerMacro(new SourcepathEntryMacro());
    registerMacro(new ProjectFileDirMacro());
    registerMacro(new ProjectNameMacro());
    registerMacro(new ProjectPathMacro());
    registerMacro(new ContentRootMacro());

    registerMacro(new ModuleFilePathMacro());
    registerMacro(new ModuleFileDirMacro());
    registerMacro(new ModuleNameMacro());
    registerMacro(new AffectedModuleNamesMacro());
    registerMacro(new CompilerContextMakeMacro());
    registerMacro(new ModulePathMacro());
    registerMacro(new ModuleSdkPathMacro());

    registerMacro(new FileRelativePathMacro());
    registerMacro(new FileRelativeDirMacro());
    registerMacro(new LineNumberMacro());
    registerMacro(new ColumnNumberMacro());

    registerMacro(new ClipboardContentMacro());
    registerMacro(new SelectedTextMacro());
    registerMacro(new SelectionStartLineMacro());
    registerMacro(new SelectionStartColumnMacro());
    registerMacro(new SelectionEndLineMacro());
    registerMacro(new SelectionEndColumnMacro());

    registerMacro(new OsNameMacro());
    registerMacro(new OsUserMacro());
    registerMacro(new TempDirMacro());

    if (File.separatorChar != '/') {
      registerMacro(new FileDirRelativeToProjectRootMacro2());
      registerMacro(new FilePathRelativeToProjectRootMacro2());
      registerMacro(new FileDirRelativeToSourcepathMacro2());
      registerMacro(new FilePathRelativeToSourcepathMacro2());
      registerMacro(new FileRelativeDirMacro2());
      registerMacro(new FileRelativePathMacro2());
    }
  }

  private void registerMacro(@NotNull Macro macro) {
    predefinedMacros.add(macro);
  }

  /**
   * @return all macros (built-in and provided via {@link Macro} extension point)
   */
  public @NotNull @Unmodifiable Collection<Macro> getMacros() {
    return ContainerUtil.concat(predefinedMacros, Macro.EP_NAME.getExtensionList());
  }

  /**
   * @deprecated Not needed anymore
   */
  @Deprecated
  public void cacheMacrosPreview(@SuppressWarnings("unused") DataContext dataContext) {
    Logger.getInstance(MacroManager.class).error("This method not needed anymore");
  }

  public static DataContext getCorrectContext(DataContext dataContext) {
    if (PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext) != null) {
      return dataContext;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return dataContext;
    }
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    if (editorManager == null) {
      return dataContext;
    }
    VirtualFile[] files = editorManager.getSelectedFiles();
    if (files.length == 0) {
      return dataContext;
    }
    FileEditor fileEditor = editorManager.getSelectedEditor(files[0]);
    return fileEditor == null ? dataContext : DataManager.getInstance().getDataContext(fileEditor.getComponent());
  }

  public static boolean containsMacros(@Nullable String str) {
    if (str == null) return false;
    return MACRO_PATTERN.matcher(str).find();
  }

  /**
   * Expands macros in a string.
   * Macros returning {@code null} from {@link Macro#expand(DataContext)} or {@link Macro#expand(DataContext, String...)}
   * are replaced with empty strings.
   *
   * @param str              string possibly containing macros
   * @param firstQueueExpand expand only macros that do not implement {@link SecondQueueExpandMacro}
   * @param dataContext      data context used for macro expansion
   * @return string with macros expanded
   * @throws ExecutionCancelledException can be thrown by any macro's expand method to stop expansion
   */
  public @Nullable String expandMacrosInString(@Nullable String str, boolean firstQueueExpand, DataContext dataContext)
    throws ExecutionCancelledException {
    return expandMacrosInString(str, firstQueueExpand, dataContext, false);
  }

  /**
   * Expands only macros not requiring interaction with a user (not implementing {@link PromptingMacro}).
   * Macros requiring input from the user or returning {@code null}
   * from {@link Macro#expand(DataContext)} or {@link Macro#expand(DataContext, String...)}
   * are replaced with empty strings.
   *
   * @param str              string possibly containing macros
   * @param firstQueueExpand expand only macros that do not implement {@link SecondQueueExpandMacro}
   * @param dataContext      data context used for macro expansion
   * @return string with macros expanded
   * @throws ExecutionCancelledException can be thrown by any macro's expand method to stop expansion
   */
  public @Nullable String expandSilentMacros(@Nullable String str, boolean firstQueueExpand, DataContext dataContext)
    throws ExecutionCancelledException {
    return expandMacrosInString(str, firstQueueExpand, dataContext, true);
  }

  /**
   * Expands macros in a string.
   *
   * @param str                string possibly containing macros
   * @param firstQueueExpand   expand only macros that do not implement {@link SecondQueueExpandMacro}
   * @param dataContext        data context used for macro expansion
   * @param onlySilent         does not expand macros that may require interaction with a user;
   *                           {@code defaultExpandValue} will be used for such macros
   * @return string with macros expanded or {@code null} if some macro is expanded to {@code null}
   * and {@code defaultExpandValue} is {@code null}
   * @throws ExecutionCancelledException can be thrown by any macro's expand method to stop expansion
   */
  public @Nullable String expandMacrosInString(@Nullable String str,
                                     boolean firstQueueExpand,
                                     DataContext dataContext,
                                     boolean onlySilent) throws ExecutionCancelledException {
    @NotNull Collection<Macro> macros = getMacros();
    if (onlySilent) {
      macros = ContainerUtil.map(macros, macro -> macro instanceof PromptingMacro ? new Macro.Silent(macro, "") : macro);
    }
    return expandMacroSet(str, firstQueueExpand, dataContext, macros);
  }

  private static @Nullable String expandMacroSet(@Nullable String str,
                                                 boolean firstQueueExpand,
                                                 DataContext dataContext,
                                                 Collection<? extends Macro> macros) throws ExecutionCancelledException {
    if (str == null) {
      return null;
    }

    MacroPathConverter converter = dataContext.getData(PATH_CONVERTER_KEY);
    List<Macro> list = ContainerUtil.filter(macros, macro -> !(macro instanceof SecondQueueExpandMacro) || firstQueueExpand);
    return expandMacros(str, list, (macro, occurence) -> {
      String expanded = macro.expandOccurence(dataContext, occurence);
      return convertPathIfNeeded(converter, macro, StringUtil.notNullize(expanded));
    });
  }

  private static String convertPathIfNeeded(@Nullable MacroPathConverter converter, @NotNull Macro macro, @NotNull String expandedValue) {
    if (converter == null) {
      return expandedValue;
    }
    if (macro instanceof PathMacro) {
      return converter.convertPath(expandedValue);
    }
    if (macro instanceof PathListMacro) {
      return converter.convertPathList(expandedValue);
    }
    return expandedValue;
  }

  @FunctionalInterface
  public interface MacroExpander {
    @Nullable String expand(Macro macro, @NotNull String occurence) throws ExecutionCancelledException;
  }

  public static @NotNull String expandMacros(@NotNull String input, @NotNull Collection<Macro> macros, @NotNull MacroExpander expander) throws ExecutionCancelledException {
    StringBuilder builder = new StringBuilder(input);
    for (Macro macro : macros) {
      expandMacro(macro, expander, builder);
    }
    return builder.toString();
  }

  private static void expandMacro(Macro macro, @NotNull MacroExpander expander, StringBuilder builder) throws ExecutionCancelledException {
    int offset = 0;
    TextRange range;
    while (offset >= 0 && offset < builder.length() && (range = macro.findOccurence(builder, offset)) != null ) {
      String occurence = builder.substring(range.getStartOffset(), range.getEndOffset());
      String value = StringUtil.notNullize(expander.expand(macro, occurence));
      builder.replace(range.getStartOffset(), range.getEndOffset(), value);
      offset += value.length();
    }
  }
}
