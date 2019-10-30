// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

public class CodeStyleSettingsManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsManager.class);

  /**
   * @deprecated Use {@link #setMainProjectCodeStyle(CodeStyleSettings)} or {@link #getMainProjectCodeStyle()} instead
   */
  @Deprecated
  @Nullable
  public volatile CodeStyleSettings PER_PROJECT_SETTINGS;

  public volatile boolean USE_PER_PROJECT_SETTINGS;
  public volatile String PREFERRED_PROJECT_CODE_STYLE;
  private volatile CodeStyleSettings myTemporarySettings;

  private final List<CodeStyleSettingsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public static CodeStyleSettingsManager getInstance(@Nullable Project project) {
    if (project == null || project.isDefault()) {
      return getInstance();
    }

    return ServiceManager.getService(project, ProjectCodeStyleSettingsManager.class);
  }

  /**
   * @deprecated Use {@link CodeStyle#getDefaultSettings()} instead.
   */
  @Deprecated
  public static CodeStyleSettingsManager getInstance() {
    return ServiceManager.getService(AppCodeStyleSettingsManager.class);
  }

  protected final void registerExtensionPointListeners(@NotNull Disposable disposable) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      FileIndentOptionsProvider.EP_NAME.addExtensionPointListener(
        new ExtensionPointListener<FileIndentOptionsProvider>() {
          @Override
          public void extensionAdded(@NotNull FileIndentOptionsProvider extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
            notifyCodeStyleSettingsChanged();
          }

          @Override
          public void extensionRemoved(@NotNull FileIndentOptionsProvider extension,
                                       @NotNull PluginDescriptor pluginDescriptor) {
            notifyCodeStyleSettingsChanged();
          }
        }, disposable);
    }
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

  /**
   * @deprecated see comments for {@link #getSettings(Project)}
   */
  @Deprecated
  @NotNull
  public CodeStyleSettings getCurrentSettings() {
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
      DefaultJDOMExternalizer.writeExternal(this, result, new DifferenceFilter<CodeStyleSettingsManager>(this, new CodeStyleSettingsManager()){
        @Override
        public boolean isAccept(@NotNull Field field) {
          return !isIgnoredOnSave(field.getName()) && super.isAccept(field);
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
   * @deprecated unused
   */
  @Deprecated
  public boolean isLoaded() {
    return true;
  }

  /**
   * @see #dropTemporarySettings()
   */
  public void setTemporarySettings(@NotNull CodeStyleSettings settings) {
    myTemporarySettings = settings;
  }

  public void dropTemporarySettings() {
    myTemporarySettings = null;
  }

  @Nullable
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
        CodeStyleSettingsManager projectInstance = ServiceManager.getService(project, ProjectCodeStyleSettingsManager.class);
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
  }
}
