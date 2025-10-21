// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.joinLines.JoinedLinesSpacingCalculator;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProviderEP;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Math.max;

/**
 * Utility class for miscellaneous code style-related methods.
 */
public final class CodeStyle {

  private CodeStyle() {
  }

  public static final Logger LOG = Logger.getInstance(CodeStyle.class);

  /**
   * @return Default application-wide root code style settings.
   */
  public static @NotNull CodeStyleSettings getDefaultSettings() {
    //noinspection deprecation
    return CodeStyleSettingsManager.getInstance().getCurrentSettings();
  }

  /**
   * Returns root code style settings for the given project. For configurable language settings use {@link #getLanguageSettings(PsiFile)} or
   * {@link #getLanguageSettings(PsiFile, Language)}.
   * @param project The project to get code style settings for.
   * @return The current root code style settings associated with the project.
   */
  public static @NotNull CodeStyleSettings getSettings(@NotNull Project project) {
    //noinspection deprecation
    return CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
  }

  /**
   * Returns either project settings if the project is not null or default application-wide settings otherwise.
   *
   * @param project The project to return the settings for or {@code null} for default settings.
   * @return Project or default code style settings.
   */
  public static @NotNull CodeStyleSettings getProjectOrDefaultSettings(@Nullable Project project) {
    return project != null ? getSettings(project) : getDefaultSettings();
  }

  /**
   * Returns root {@link CodeStyleSettings} for the given project and virtual file.
   * <p>
   * In some cases the returned instance may be of
   * {@link TransientCodeStyleSettings} class if the original (project) settings are modified for specific file by
   * {@link CodeStyleSettingsModifier} extensions. In these cases the returned instance may change upon the next call if some of
   * {@link TransientCodeStyleSettings} dependencies become outdated.
   * Subscribe to {@link CodeStyleSettingsListener#TOPIC} to receive notifications when the associated settings change.
   *
   * @param project The current project.
   * @param file The file to get code style settings for.
   * @return The current root code style settings associated with the file or default settings if the file is invalid.
   */
  public static @NotNull CodeStyleSettings getSettings(@NotNull Project project, @NotNull VirtualFile file) {
    CodeStyleSettings localOrTempSettings = getLocalOrTemporarySettings(project);
    if (localOrTempSettings != null) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("localOrTemp settings for " + file.getName());
      }
      return localOrTempSettings;
    }
    return getCachedOrProjectSettings(project, file);
  }

  public static @NotNull CodeStyleSettings getSettings(@NotNull PsiFile file) {
    final Project project = file.getProject();

    CodeStyleSettings localOrTempSettings = getLocalOrTemporarySettings(project);
    if (localOrTempSettings != null) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("localOrTemp settings for " + file.getName());
      }
      return localOrTempSettings;
    }
    PsiFile topLevel = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    PsiFile settingsFile = getSettingsPsi(topLevel != null ? topLevel : file);
    if (settingsFile == null) {
      return getSettings(project);
    }
    VirtualFile virtualFile = settingsFile.getVirtualFile();
    if (virtualFile == null) {
      return getSettings(project);
    }
    return getCachedOrProjectSettings(project, virtualFile);
  }

  private static @NotNull CodeStyleSettings getCachedOrProjectSettings(@NotNull Project project, @NotNull VirtualFile file) {
    CodeStyleSettings cachedSettings = CodeStyleCachingService.getInstance(project).tryGetSettings(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug((cachedSettings != null ? "cached" : "project") + " settings for " + file.getName());
    }
    return cachedSettings != null ? cachedSettings : getSettings(project);
  }

  private static @Nullable CodeStyleSettings getLocalOrTemporarySettings(@NotNull Project project) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    CodeStyleSettings localSettings = settingsManager.getLocalSettings();
    if (localSettings != null) {
      return localSettings;
    }

    @SuppressWarnings("TestOnlyProblems")
    CodeStyleSettings tempSettings = settingsManager.getTemporarySettings();
    if (tempSettings != null) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn("Temporary settings used in production. Please use CodeStyle#runWithLocalSettings instead.");
      }
      return tempSettings;
    }

    return null;
  }

  /**
   * Finds a PSI file to be used to retrieve code style settings. May use {@link PsiFileFactory#ORIGINAL_FILE} if the
   * given file doesn't match conditions needed to get the settings, e.g., if it's not associated with a virtual file
   * in the local file system.
   *
   * @param file The initial file.
   * @return The PSI file or {@code null} if neither the initial file nor any other associated file can be used for settings.
   */
  public static @Nullable PsiFile getSettingsPsi(@NotNull PsiFile file) {
    if (hasLocalVirtualFile(file)) return file;
    PsiFile originalFile = file.getUserData(PsiFileFactory.ORIGINAL_FILE);
    if (originalFile != null) {
      return getSettingsPsi(originalFile);
    }
    return null;
  }


  private static boolean hasLocalVirtualFile(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && virtualFile.isInLocalFileSystem();
  }

  public static @NotNull CodeStyleSettings getSettings(@NotNull Project project, @NotNull Document document) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    return file != null ? getSettings(file) : getSettings(project);
  }

  public static @NotNull CodeStyleSettings getSettings(@NotNull Editor editor) {
    Project project = editor.getProject();
    VirtualFile file = editor.getVirtualFile();
    if (file != null && project != null) {
      return getSettings(project, file);
    }
    return getDefaultSettings();
  }

  /**
   * Returns language code style settings for the specified editor. If the editor has an associated document and PSI file, returns
   * settings for the PSI file or {@code null} otherwise.
   *
   * @param editor The editor to get settings for.
   * @return The language code style settings for the editor or {@code null}.
   */
  public static @Nullable CommonCodeStyleSettings getLanguageSettings(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        return getLanguageSettings(file);
      }
    }
    return null;
  }

  /**
   * Returns custom settings for the given PSI file.
   * @param file The file.
   * @param customSettingsClass The class of a settings object to be returned.
   * @param <T> Settings class type.
   * @return The current custom settings associated with the PSI file.
   */
  public static @NotNull <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull PsiFile file, @NotNull Class<T> customSettingsClass) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCustomSettings(customSettingsClass);
  }

  /**
   * Returns language settings for the given PSI file. The language is taken from the file itself.
   * @param file The file to retrieve language settings for.
   * @return The associated language settings.
   */
  public static @NotNull CommonCodeStyleSettings getLanguageSettings(@NotNull PsiFile file) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCommonSettings(file.getLanguage());
  }

  /**
   * Returns language settings for the given PSI file and language. This method may be useful when PSI file contains elements for multiple
   * languages and language settings should be taken from a specific language context.
   * @param file The file to retrieve language settings for.
   * @return The associated language settings.
   */
  public static @NotNull CommonCodeStyleSettings getLanguageSettings(@NotNull PsiFile file, @NotNull Language language) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getCommonSettings(language);
  }

  /**
   * Works similarly to {@link #getIndentOptions(Project, VirtualFile)} but for a PSI file.
   */
  public static @NotNull CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull PsiFile file) {
    CodeStyleSettings rootSettings = getSettings(file);
    return rootSettings.getIndentOptionsByFile(file);
  }

  /**
   * Returns indent options for the given project and virtual file. The method attempts to use {@link FileIndentOptionsProvider}
   * if applicable to the file. If there are no suitable indent options providers, it takes configurable language indent options or
   * retrieves indent options by file type using {@link CodeStyleSettings#getIndentOptions(FileType)}.
   *
   * @param project The current project.
   * @param virtualFile The virtual file to get the indent options for.
   * @return The resulting indent options.
   * @see FileIndentOptionsProvider
   */
  public static @NotNull CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    CodeStyleSettings rootSetting = getSettings(project, virtualFile);
    return rootSetting.getIndentOptionsByFile(project, virtualFile, null);
  }

  /**
   * Returns indent options for the given project and document.
   * @param project The current project.
   * @param document The document to get indent options for.
   * @return The indent options associated with document's PSI file if the file is available or other indent options otherwise.
   */
  public static @NotNull CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project, @NotNull Document document) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file != null) {
      return getIndentOptions(file);
    }
    return getSettings(project).getIndentOptions(null);
  }

  /**
   * Returns indent size for the given PSI file.
   * @param file The file to get indent size for.
   * @return The indent size to be used with the PSI file.
   */
  public static int getIndentSize(@NotNull PsiFile file) {
    return getIndentOptions(file).INDENT_SIZE;
  }

  /**
   * Set temporary settings for the project. Temporary settings will override any user settings until {@link #dropTemporarySettings(Project)}
   * is called.
   * <p>
   *   <b>Note</b>
   * The method is supposed to be used in test's {@code setUp()} method. In production code use
   * {@link #runWithLocalSettings(Project, CodeStyleSettings, Runnable)}
   * or {@link #runWithLocalSettings(Project, CodeStyleSettings, Consumer)}.
   *
   * @param project The project or {@code null} for default settings.
   * @param settings The settings to use temporarily with the project.
   */
  @TestOnly
  public static void setTemporarySettings(@Nullable Project project, @NotNull CodeStyleSettings settings) {
    CodeStyleSettingsManager.getInstance(project).setTemporarySettings(settings);
  }


  /**
   * Drop temporary settings.
   * <p>
   *   <b>Note</b>
   * The method is supposed to be used in test's {@code tearDown()} method. In production code use
   * {@link #runWithLocalSettings(Project, CodeStyleSettings, Runnable)}
   * or {@link #runWithLocalSettings(Project, CodeStyleSettings, Consumer)}.
   *
   * @param project The project to drop temporary settings for or {@code null} for default settings.
   * @see #setTemporarySettings(Project, CodeStyleSettings)
   */
  @TestOnly
  public static void dropTemporarySettings(@Nullable Project project) {
    CodeStyleSettingsManager codeStyleSettingsManager;
    if (project == null || project.isDefault()) {
      codeStyleSettingsManager = ApplicationManager.getApplication().getServiceIfCreated(AppCodeStyleSettingsManager.class);
    }
    else {
      codeStyleSettingsManager = project.getServiceIfCreated(ProjectCodeStyleSettingsManager.class);
    }

    if (codeStyleSettingsManager != null) {
      codeStyleSettingsManager.dropTemporarySettings();
    }
  }

  /**
   * Invoke a runnable using the specified settings.
   * <p>
   * Inside the <code>runnable</code>, <code>localSettings</code> override code style settings for all files associated with
   * <code>project</code>. This effect is limited to the current thread.
   * <p>
   * <code>localSettings</code> <b>must not</b> be an instance representing actual settings
   * (e.g. the output of {@link CodeStyle#getSettings}).
   * It is preferable to use {@link CodeStyle#runWithLocalSettings(Project, CodeStyleSettings, Consumer)}
   * or {@link CodeStyle#computeWithLocalSettings(Project, CodeStyleSettings, Function)} instead,
   * which provides a copy of provided base settings out-of-the-box.
   *
   * @param project The current project.
   * @param localSettings The local settings.
   * @param runnable The runnable.
   */
  public static void runWithLocalSettings(@NotNull Project project,
                                          @NotNull CodeStyleSettings localSettings,
                                          @NotNull Runnable runnable) {
    CodeStyleSettingsManager.getInstance(project).runWithLocalSettings(localSettings, runnable);
  }

  /**
   * Invoke the specified consumer with a copy of the given <code>baseSettings</code>.
   * <p>
   * Inside <code>localSettingsConsumer</code>, this copy will override code style settings for all files associated with <code>project</code>.
   * This effect is limited to the current thread. It is safe to make any changes to the copy of settings passed to the consumer, these changes
   * will not affect any currently set code style.
   *
   * @param project              The current project.
   * @param baseSettings         The base settings to be cloned and used in consumer.
   * @param localSettingsConsumer The consumer to execute with the base settings copy.
   */
  public static void runWithLocalSettings(@NotNull Project project,
                                          @NotNull CodeStyleSettings baseSettings,
                                          @NotNull Consumer<? super @NotNull CodeStyleSettings> localSettingsConsumer) {
    CodeStyleSettingsManager.getInstance(project).runWithLocalSettings(baseSettings, localSettingsConsumer);
  }

  /**
   * Invoke the specified function with a copy of the given <code>baseSettings</code>.
   * <p>
   * Inside <code>localSettingsFunction</code>,
   * this copy will override code style settings for all files associated with <code>project</code>.
   * This effect is limited to the current thread.
   * It is safe to make any changes to the copy of settings passed to the function, these changes
   * will not affect any currently set code style.
   *
   * @param project               The current project.
   * @param baseSettings          The base settings to be cloned and used in the function.
   * @param localSettingsFunction The function to execute with the base settings copy.
   */
  public static <T> T computeWithLocalSettings(@NotNull Project project,
                                               @NotNull CodeStyleSettings baseSettings,
                                               @NotNull Function<? super @NotNull CodeStyleSettings, T> localSettingsFunction) {
    return CodeStyleSettingsManager.getInstance(project).computeWithLocalSettings(baseSettings, localSettingsFunction);
  }

  /**
   * Execute the specified runnable with the given temporary code style settings and restore the old settings even if the runnable fails
   * with an exception.
   * <p>
   * For production code use {@link #runWithLocalSettings(Project, CodeStyleSettings, Runnable)}
   *
   * @param project       The current project.
   * @param tempSettings  The temporary code style settings.
   * @param runnable      The runnable to execute with the temporary settings.
   */
  @TestOnly
  public static void doWithTemporarySettings(@NotNull Project project,
                                             @NotNull CodeStyleSettings tempSettings,
                                             @NotNull Runnable runnable) {
    CodeStyleSettingsManager.getInstance(project).doWithTemporarySettings(tempSettings, runnable);
  }

  /**
   * Invoke the specified consumer with a copy of the given <code>baseSettings</code> and restore the old settings even if the
   * consumer fails with an exception. It is safe to make any changes to the copy of settings passed to consumer, these
   * changes will not affect any currently set code style.
   * <p>
   * For production code use {@link #runWithLocalSettings(Project, CodeStyleSettings, Consumer)}
   *
   * @param project              The current project.
   * @param baseSettings         The base settings to be cloned and used in consumer.
   * @param tempSettingsConsumer The consumer to execute with the base settings copy.
   */
  @TestOnly
  public static void doWithTemporarySettings(@NotNull Project project,
                                             @NotNull CodeStyleSettings baseSettings,
                                             @NotNull Consumer<? super CodeStyleSettings> tempSettingsConsumer) {
    CodeStyleSettingsManager.getInstance(project).doWithTemporarySettings(baseSettings, tempSettingsConsumer);
  }

  /**
   * @param project The project to check.
   * @return {@code true} if the project uses its own project code style, {@code false} if global (application-level) code style settings
   *         are used.
   */
  public static boolean usesOwnSettings(@NotNull Project project) {
    return CodeStyleSettingsManager.getInstance(project).USE_PER_PROJECT_SETTINGS;
  }

  /**
   * Assign main project-wide code style settings and force the project to use its own code style instead of a global (application) one.
   *
   * @param project   The project to assign the settings to.
   * @param settings  The settings to use with the project.
   */
  public static void setMainProjectSettings(@NotNull Project project, @NotNull CodeStyleSettings settings) {
    CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(project);
    codeStyleSettingsManager.setMainProjectCodeStyle(settings);
    codeStyleSettingsManager.USE_PER_PROJECT_SETTINGS = true;
  }

  /**
   * Checks if the file can be formatted according to code style settings. If formatting is disabled, all related operations including
   * optimize imports and rearrange code should be blocked (cause no changes).
   *
   * @param file The PSI file to check.
   * @return True if the file is formattable, false otherwise.
   */
  public static boolean isFormattingEnabled(@NotNull PsiFile file) {
    return !getSettings(file).getExcludedFiles().contains(file);
  }

  /**
   * Reformat the given {@code fileToReformat} using code style settings for the {@code contextFile}. The method may be
   * useful to reformat a fragment of code (temporary file) which eventually will be inserted to the context file.
   *
   * @param fileToReformat The file to reformat (may be a temporary dummy file).
   * @param contextFile    The actual (target) file whose settings must be used.
   */
  public static void reformatWithFileContext(@NotNull PsiFile fileToReformat, @NotNull PsiFile contextFile) {
    final Project project = contextFile.getProject();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    CodeStyleSettings realFileSettings = getSettings(contextFile);
    runWithLocalSettings(project, realFileSettings, () -> codeStyleManager.reformat(fileToReformat));
  }


  /**
   * Create an instance of {@code CodeStyleSettings} with settings copied from {@code baseSettings}
   * for testing purposes.
   * @param baseSettings Base settings to be used for created {@code CodeStyleSettings} instance.
   * @return Test code style settings.
   */
  @TestOnly
  public static @NotNull CodeStyleSettings createTestSettings(@Nullable CodeStyleSettings baseSettings) {
    return CodeStyleSettingsManager.createTestSettings(baseSettings);
  }

  /**
   * Calculates the indent that should be used for the line at specified offset in the specified
   * editor. If there is a suitable {@code LineIndentProvider} for the language, it will be used to calculate the indent. Otherwise, if
   * {@code allowDocCommit} flag is true, the method will use formatter on committed document.
   *
   * @param editor   The editor for which the indent must be returned.
   * @param language Context language
   * @param offset   The caret offset in the editor.
   * @param allowDocCommit Allow calculation using committed document.
   *                       <p>
   *                         <b>NOTE: </b> Committing the document may be slow an cause performance issues on large files.
   * @return the indent string (containing of tabs and/or white spaces), or null if it
   *         was not possible to calculate the indent.
   */
  public static String getLineIndent(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    Project project = editor.getProject();
    if (project == null) return null;
    LineIndentProvider lineIndentProvider = LineIndentProviderEP.findLineIndentProvider(language);
    String indent = lineIndentProvider != null ? lineIndentProvider.getLineIndent(project, editor, language, offset) : null;
    if (Strings.areSameInstance(indent, LineIndentProvider.DO_NOT_ADJUST)) {
      return allowDocCommit ? null : indent;
    }
    return indent != null ? indent : allowDocCommit ? getLineIndent(project, editor.getDocument(), offset) : null;
  }

  private static @Nullable String getLineIndent(@Nullable Project project, final @NotNull Document document, int offset) {
    if (project == null) return null;
    PsiDocumentManager.getInstance(project).commitDocument(document);
    return CodeStyleManager.getInstance(project).getLineIndent(document, offset);
  }

  /**
   * Calculates the spacing (in columns) for joined lines at given offset after join lines or smart backspace actions.
   * If there is a suitable {@code LineIndentProvider} for the language,
   * it will be used to calculate the spacing. Otherwise, if
   * {@code allowDocCommit} flag is true, the method will use formatter on committed document.
   *
   * @param editor   The editor for which the spacing must be returned.
   * @param language Context language
   * @param offset   Offset in the editor after the indent in the second joining line.
   * @param allowDocCommit Allow calculation using committed document.
   *                       <p>
   *                         <b>NOTE: </b> Committing the document may be slow an cause performance issues on large files.
   * @return non-negative spacing between end- and start-line tokens after the join.
   */
  public static int getJoinedLinesSpacing(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    Project project = editor.getProject();
    if (project == null) return 0;
    LineIndentProvider lineIndentProvider = LineIndentProviderEP.findLineIndentProvider(language);
    int space = lineIndentProvider instanceof JoinedLinesSpacingCalculator
                ? ((JoinedLinesSpacingCalculator)lineIndentProvider).getJoinedLinesSpacing(project, editor, language, offset)
                : -1;
    if (space < 0 && allowDocCommit) {
      final Document document = editor.getDocument();
      PsiDocumentManager.getInstance(project).commitDocument(document);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null) return 0;
      return max(0, CodeStyleManager.getInstance(project).getSpacing(file, offset));
    }
    return max(0, space);
  }

  /**
   * Create a clean instance of {@code CodeStyleSettings} for testing purposes.
   * @return Test code style settings.
   */
  @TestOnly
  public static @NotNull CodeStyleSettings createTestSettings() {
    return CodeStyleSettingsManager.createTestSettings(null);
  }

  public static @NotNull CodeStyleSettingsFacade getFacade(@NotNull PsiFile file) {
    return new DefaultCodeStyleSettingsFacade(getSettings(file), file.getFileType());
  }

  public static @NotNull CodeStyleSettingsFacade getFacade(@NotNull Project project, @NotNull Document document, @NotNull FileType fileType) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    return new DefaultCodeStyleSettingsFacade(psiFile == null ? getSettings(project) : getSettings(psiFile), fileType);
  }

  /**
   * Finds a language at the specified offset and common language settings for it.
   *
   * @param editor The current editor.
   * @param offset The offset to find the language at.
   */
  public static @NotNull CommonCodeStyleSettings getLocalLanguageSettings(@NotNull Editor editor, int offset) {
    PsiFile psiFile = PsiEditorUtil.getPsiFile(editor);
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, offset);
    return getLanguageSettings(psiFile, language);
  }
}
