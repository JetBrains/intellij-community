// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

public class CodeStyleSettingsManager implements PersistentStateComponentWithModificationTracker<Element> {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsManager.class);

  /**
   * @deprecated Use {@link #setMainProjectCodeStyle(CodeStyleSettings)} or {@link #getMainProjectCodeStyle()} instead
   */
  @Deprecated(forRemoval = true) public volatile @Nullable CodeStyleSettings PER_PROJECT_SETTINGS;

  public volatile boolean USE_PER_PROJECT_SETTINGS;
  public volatile String PREFERRED_PROJECT_CODE_STYLE;
  private volatile CodeStyleSettings myTemporarySettings;
  
  private final ThreadLocal<CodeStyleSettings> myLocalSettings = new ThreadLocal<>();

  private static final WeakList<CodeStyleSettings> ourReferencedSettings = new WeakList<>();

  public @NotNull CodeStyleSettings createSettings() {
    return createSettings(true);
  }

  @ApiStatus.Internal
  public @NotNull CodeStyleSettings createSettings(boolean loadExtensions) {
    CodeStyleSettings newSettings = new CodeStyleSettings(loadExtensions, false);
    registerSettings(newSettings);
    return newSettings;
  }

  static void registerSettings(@NotNull CodeStyleSettings newSettings) {
    ourReferencedSettings.add(newSettings);
  }

  @TestOnly
  public final @NotNull CodeStyleSettings createTemporarySettings() {
    CodeStyleSettings temporarySettings = new CodeStyleSettings(true, false);
    myTemporarySettings = temporarySettings;
    return temporarySettings;
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

  /**
   * @see CodeStyle#runWithLocalSettings(Project, CodeStyleSettings, Runnable)
   */
  public void runWithLocalSettings(@NotNull CodeStyleSettings localSettings,
                                   @NotNull Runnable runnable) {
    CodeStyleSettings tempSettingsBefore = myLocalSettings.get();
    try {
      myLocalSettings.set(localSettings);
      runnable.run();
    }
    finally {
      myLocalSettings.set(tempSettingsBefore);
    }
  }

  /**
   * @see CodeStyle#runWithLocalSettings(Project, CodeStyleSettings, Consumer)
   */
  public void runWithLocalSettings(@NotNull CodeStyleSettings baseSettings,
                                   @NotNull Consumer<? super @NotNull CodeStyleSettings> localSettingsConsumer) {
    computeWithLocalSettings(baseSettings, localSettings -> {
      localSettingsConsumer.accept(localSettings);
      return null;
    });
  }

  /**
   * @see CodeStyle#computeWithLocalSettings(Project, CodeStyleSettings, Function)
   */
  public <T> T computeWithLocalSettings(@NotNull CodeStyleSettings baseSettings,
                                        @NotNull Function<? super @NotNull CodeStyleSettings, T> localSettingsFunction) {
    CodeStyleSettings localSettingsBefore = myLocalSettings.get();
    try {
      CodeStyleSettings localSettings = new CodeStyleSettings(true, false);
      localSettings.copyFrom(baseSettings);
      myLocalSettings.set(localSettings);
      return localSettingsFunction.apply(localSettings);
    }
    finally {
      myLocalSettings.set(localSettingsBefore);
    }
  }

  /**
   * @see CodeStyle#doWithTemporarySettings(Project, CodeStyleSettings, Runnable)
   */
  @TestOnly
  public void doWithTemporarySettings(@NotNull CodeStyleSettings tempSettings,
                                      @NotNull Runnable runnable) {
    CodeStyleSettings tempSettingsBefore = getTemporarySettings();
    try {
      setTemporarySettings(tempSettings);
      runnable.run();
    }
    finally {
      if (tempSettingsBefore != null) {
        setTemporarySettings(tempSettingsBefore);
      }
      else {
        dropTemporarySettings();
      }
    }
  }

  /**
   * @see CodeStyle#doWithTemporarySettings(Project, CodeStyleSettings, Consumer)
   */
  @TestOnly
  public void doWithTemporarySettings(@NotNull CodeStyleSettings baseSettings,
                                      @NotNull Consumer<? super CodeStyleSettings> tempSettingsConsumer) {
    CodeStyleSettings tempSettingsBefore = getTemporarySettings();
    try {
      CodeStyleSettings tempSettings = createTemporarySettings();
      tempSettings.copyFrom(baseSettings);
      tempSettingsConsumer.accept(tempSettings);
    }
    finally {
      if (tempSettingsBefore != null) {
        setTemporarySettings(tempSettingsBefore);
      }
      else {
        dropTemporarySettings();
      }
    }
  }

  private @NotNull @Unmodifiable Collection<CodeStyleSettings> getAllSettings() {
    return ourReferencedSettings.toStrongList();
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

  protected @NotNull @Unmodifiable Collection<CodeStyleSettings> enumSettings() { return Collections.emptyList(); }

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
    CodeStyleSettings.getDefaults().removeCustomSettings(provider);
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
  @Deprecated
  public static @NotNull CodeStyleSettings getSettings(final @Nullable Project project) {
    return getInstance(project).getCurrentSettings();
  }

  /**
   * @deprecated see comments for {@link #getSettings(Project)} or {@link CodeStyle#getDefaultSettings()}
   */
  @Deprecated
  public @NotNull CodeStyleSettings getCurrentSettings() {
    CodeStyleSettings localSettings = getLocalSettings();
    if (localSettings != null) return localSettings;
    CodeStyleSettings temporarySettings = myTemporarySettings;
    if (temporarySettings != null) return temporarySettings;
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
  public @Nullable CodeStyleSettings getMainProjectCodeStyle() {
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

  @TestOnly
  public @Nullable CodeStyleSettings getTemporarySettings() {
    return myTemporarySettings;
  }
  
  @ApiStatus.Internal
  public @Nullable CodeStyleSettings getLocalSettings() {
    return myLocalSettings.get();
  }

  protected @NotNull MessageBus getMessageBus() {
    throw new UnsupportedOperationException("The method is not implemented");
  }

  public void subscribe(@NotNull CodeStyleSettingsListener listener, @NotNull Disposable disposable) {
    getMessageBus().connect(disposable).subscribe(CodeStyleSettingsListener.TOPIC, listener);
  }

  public void subscribe(@NotNull CodeStyleSettingsListener listener) {
    getMessageBus().connect().subscribe(CodeStyleSettingsListener.TOPIC, listener);
  }

  @ApiStatus.Internal
  public void fireCodeStyleSettingsChanged(@NotNull VirtualFile file, @Nullable CodeStyleSettings settings) {
    if (getProject() != null) {
      fireCodeStyleSettingsChanged(new CodeStyleSettingsChangeEvent(getProject(), file, settings));
    }
  }

  public void fireCodeStyleSettingsChanged(@NotNull VirtualFile file) {
    fireCodeStyleSettingsChanged(file, null);
  }

  public void fireCodeStyleSettingsChanged() {
    if (getProject() != null) {
      fireCodeStyleSettingsChanged(new CodeStyleSettingsChangeEvent(getProject(), null));
    }
  }

  private void fireCodeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    MessageBus bus = getMessageBus();
    if (!bus.isDisposed()) {
      bus.syncPublisher(CodeStyleSettingsListener.TOPIC).codeStyleSettingsChanged(event);
    }
  }

  protected @Nullable Project getProject() {
    ProjectManager projectManager = ProjectManager.getInstance();
    return projectManager != null ? projectManager.getDefaultProject() : null;
  }

  /**
   * Increase current project's code style modification tracker and notify all the listeners on changed code style. The
   * method must be called if project code style is changed programmatically so that editors and etc. are aware of
   * code style update and refresh their settings accordingly.
   *
   * @see CodeStyleSettingsListener
   */
  public final void notifyCodeStyleSettingsChanged() {
    updateSettingsTracker();
    fireCodeStyleSettingsChanged();
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
