// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CodeStyleSettingsManager implements PersistentStateComponentWithModificationTracker<Element> {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsManager.class);

  /**
   * @deprecated Use {@link #setMainProjectCodeStyle(CodeStyleSettings)} or {@link #getMainProjectCodeStyle()} instead
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public volatile CodeStyleSettings PER_PROJECT_SETTINGS;

  public volatile boolean USE_PER_PROJECT_SETTINGS;
  public volatile String PREFERRED_PROJECT_CODE_STYLE;
  private volatile CodeStyleSettings myTemporarySettings;

  private final List<CodeStyleSettingsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final static WeakList<CodeStyleSettings> ourReferencedSettings = new WeakList<>();

  @NotNull
  public CodeStyleSettings createSettings() {
    CodeStyleSettings newSettings = new CodeStyleSettings(true, false);
    registerSettings(newSettings);
    return newSettings;
  }

  static void registerSettings(@NotNull CodeStyleSettings newSettings) {
    ourReferencedSettings.add(newSettings);
  }

  @TestOnly
  @NotNull
  public final CodeStyleSettings createTemporarySettings() {
    myTemporarySettings = new CodeStyleSettings(true, false);
    return myTemporarySettings;
  }

  @SuppressWarnings("MethodMayBeStatic")
  public final @NotNull CodeStyleSettings cloneSettings(@NotNull CodeStyleSettings settings) {
    CodeStyleSettings clonedSettings = new CodeStyleSettings(true, false);
    clonedSettings.copyFrom(settings);
    registerSettings(clonedSettings);
    return clonedSettings;
  }

  @TestOnly
  public static @NotNull CodeStyleSettings createTestSettings(@Nullable CodeStyleSettings baseSettings) {
    final CodeStyleSettings testSettings = new CodeStyleSettings(true, false);
    if (baseSettings != null) {
      testSettings.copyFrom(baseSettings);
    }
    return testSettings;
  }

  private @NotNull Collection<CodeStyleSettings> getAllSettings() {
    List<CodeStyleSettings> allSettings = new ArrayList<>(enumSettings());
    allSettings.addAll(ourReferencedSettings.toStrongList());
    return allSettings;
  }

  @Override
  public long getStateModificationCount() {
    return enumSettings().stream()
                         .mapToLong(settings -> settings.getModificationTracker().getModificationCount())
                         .sum();
  }

  public static CodeStyleSettingsManager getInstance(@Nullable Project project) {
    if (project == null || project.isDefault()) {
      return getInstance();
    }
    return project.getService(ProjectCodeStyleSettingsManager.class);
  }

  public static CodeStyleSettingsManager getInstance() {
    return ApplicationManager.getApplication().getService(AppCodeStyleSettingsManager.class);
  }

  protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
    FileIndentOptionsProvider.EP_NAME.addChangeListener(this::notifyCodeStyleSettingsChanged, disposable);
    CodeStyleSettingsService.getInstance().addListener(new CodeStyleSettingsServiceListener() {
      @Override
      public void fileTypeIndentOptionsFactoryAdded(@NotNull FileTypeIndentOptionsFactory factory) {
        registerFileTypeIndentOptions(getAllSettings(), factory.getFileType(), factory.createIndentOptions());
      }

      @Override
      public void fileTypeIndentOptionsFactoryRemoved(@NotNull FileTypeIndentOptionsFactory factory) {
        unregisterFileTypeIndentOptions(getAllSettings(), factory.getFileType());
      }

      @Override
      public void languageCodeStyleProviderAdded(@NotNull LanguageCodeStyleProvider provider) {
        registerLanguageSettings(getAllSettings(), provider);
        registerCustomSettings(getAllSettings(), provider);
      }

      @Override
      public void languageCodeStyleProviderRemoved(@NotNull LanguageCodeStyleProvider provider) {
        unregisterLanguageSettings(getAllSettings(), provider);
        unregisterCustomSettings(getAllSettings(), provider);
      }

      @Override
      public void customCodeStyleSettingsFactoryAdded(@NotNull CustomCodeStyleSettingsFactory factory) {
        registerCustomSettings(getAllSettings(), factory);
      }

      @Override
      public void customCodeStyleSettingsFactoryRemoved(@NotNull CustomCodeStyleSettingsFactory factory) {
        unregisterCustomSettings(getAllSettings(), factory);
      }
    }, disposable);
  }

  @NotNull
  protected Collection<CodeStyleSettings> enumSettings() { return Collections.emptyList(); }

  @ApiStatus.Internal
  public final void registerFileTypeIndentOptions(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                                  @NotNull FileType fileType,
                                                  @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    allSettings.forEach(settings -> settings.registerAdditionalIndentOptions(fileType, indentOptions));
    notifyCodeStyleSettingsChanged();
  }

  @ApiStatus.Internal
  public final void unregisterFileTypeIndentOptions(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                                    @NotNull FileType fileType) {
    allSettings.forEach(settings -> settings.unregisterAdditionalIndentOptions(fileType));
    notifyCodeStyleSettingsChanged();
  }

  @ApiStatus.Internal
  public final void registerLanguageSettings(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                             @NotNull LanguageCodeStyleProvider provider) {
    allSettings.forEach(settings -> settings.registerCommonSettings(provider));
    notifyCodeStyleSettingsChanged();
  }

  @ApiStatus.Internal
  public final void unregisterLanguageSettings(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                               @NotNull LanguageCodeStyleProvider provider) {
    allSettings.forEach(settings -> settings.removeCommonSettings(provider));
    notifyCodeStyleSettingsChanged();
  }

  @ApiStatus.Internal
  public final void registerCustomSettings(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                           @NotNull CustomCodeStyleSettingsFactory provider) {
    allSettings.forEach(settings -> settings.registerCustomSettings(provider));
    notifyCodeStyleSettingsChanged();
  }

  @ApiStatus.Internal
  public final void unregisterCustomSettings(@NotNull Collection<? extends CodeStyleSettings> allSettings,
                                             @NotNull CustomCodeStyleSettingsFactory provider) {
    allSettings.forEach(settings -> settings.removeCustomSettings(provider));
    notifyCodeStyleSettingsChanged();
  }

  /**
   * @deprecated Use one of the following methods:
   * <ul>
   *   <li>{@link CodeStyle#getLanguageSettings(PsiFile, Language)} to get common settings for a language.</li>
   *   <li>{@link CodeStyle#getCustomSettings(PsiFile, Class)} to get custom settings.</li>
   * </ul>
   * If {@code PsiFile} is not applicable, use {@link CodeStyle#getSettings(Project)} but only in cases
   * when using main project settings is <b>logically the only choice</b> in a given context. It shouldn't be used just because the existing
   * code doesn't allow to easily retrieve a PsiFile. Otherwise the code will not catch up with proper file code style settings since the
   * settings may differ for different files depending on their scope.
   */
  @NotNull
  @Deprecated
  public static CodeStyleSettings getSettings(@Nullable final Project project) {
    return getInstance(project).getCurrentSettings();
  }

  protected void checkState() {}

  /**
   * @deprecated see comments for {@link #getSettings(Project)} or {@link CodeStyle#getDefaultSettings()}
   */
  @Deprecated
  @NotNull
  public CodeStyleSettings getCurrentSettings() {
    CodeStyleSettings temporarySettings = myTemporarySettings;
    if (temporarySettings != null) return temporarySettings;
    checkState();
    CodeStyleSettings projectSettings = getMainProjectCodeStyle();
    if (USE_PER_PROJECT_SETTINGS && projectSettings != null) return projectSettings;
    return CodeStyleSchemes.getInstance().findPreferredScheme(PREFERRED_PROJECT_CODE_STYLE).getCodeStyleSettings();
  }

  @Override
  public Element getState() {
    Element result = new Element("state");
    try {
      //noinspection deprecation
      DefaultJDOMExternalizer.write(this, result, new DifferenceFilter<>(this, new CodeStyleSettingsManager()) {
        @Override
        public boolean test(@NotNull Field field) {
          return !isIgnoredOnSave(field.getName()) && super.test(field);
        }
      });
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return result;
  }

  protected boolean isIgnoredOnSave(@NotNull String fieldName) {
    return false;
  }

  @Override
  public void loadState(@NotNull Element state) {
    try {
      //noinspection deprecation
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  /**
   * Sets main project settings by the name "Project". For default project it's the only named code style.
   * @param settings The code style settings which can be assigned to project.
   */
  public void setMainProjectCodeStyle(@Nullable CodeStyleSettings settings) {
    PER_PROJECT_SETTINGS = settings;
  }

  /**
   * @return The main project code style settings. For default project, that's the only code style.
   */
  @Nullable
  public CodeStyleSettings getMainProjectCodeStyle() {
    return PER_PROJECT_SETTINGS;
  }

  /**
   * @see #dropTemporarySettings()
   */
  @TestOnly
  public void setTemporarySettings(@NotNull CodeStyleSettings settings) {
    myTemporarySettings = settings;
  }

  @TestOnly
  public void dropTemporarySettings() {
    myTemporarySettings = null;
  }

  @Nullable
  @TestOnly
  public CodeStyleSettings getTemporarySettings() {
    return myTemporarySettings;
  }

  public void addListener(@NotNull CodeStyleSettingsListener listener) {
    myListeners.add(listener);
  }

  private void removeListener(@NotNull CodeStyleSettingsListener listener) {
    myListeners.remove(listener);
  }

  public static void removeListener(@Nullable Project project, @NotNull CodeStyleSettingsListener listener) {
    if (project == null || project.isDefault()) {
      getInstance().removeListener(listener);
    }
    else {
      if (!project.isDisposed()) {
        CodeStyleSettingsManager projectInstance = project.getService(ProjectCodeStyleSettingsManager.class);
        if (projectInstance != null) {
          projectInstance.removeListener(listener);
        }
      }
    }
  }

  public void fireCodeStyleSettingsChanged(@Nullable PsiFile file) {
    for (CodeStyleSettingsListener listener : myListeners) {
      listener.codeStyleSettingsChanged(new CodeStyleSettingsChangeEvent(file));
    }
  }

  /**
   * Increase current project's code style modification tracker and notify all the listeners on changed code style. The
   * method must be called if project code style is changed programmatically so that editors and etc. are aware of
   * code style update and refresh their settings accordingly.
   *
   * @see CodeStyleSettingsListener
   * @see #addListener(CodeStyleSettingsListener)
   */
  public final void notifyCodeStyleSettingsChanged() {
    updateSettingsTracker();
    fireCodeStyleSettingsChanged(null);
  }

  @ApiStatus.Internal
  public void updateSettingsTracker() {
    CodeStyleSettings settings = getCurrentSettings();
    settings.getModificationTracker().incModificationCount();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Updated code style settings modification tracker to " + settings.getModificationTracker().getModificationCount());
    }
  }
}
