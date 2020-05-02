// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.ui.UINumericRange;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ReportValue;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

@State(name = "GeneralSettings", storages = @Storage(GeneralSettings.IDE_GENERAL_XML), reportStatistic = true)
public final class GeneralSettings implements PersistentStateComponent<GeneralSettings> {
  public static final String IDE_GENERAL_XML = "ide.general.xml";

  public static final int OPEN_PROJECT_ASK = -1;
  public static final int OPEN_PROJECT_NEW_WINDOW = 0;
  public static final int OPEN_PROJECT_SAME_WINDOW = 1;
  public static final int OPEN_PROJECT_SAME_WINDOW_ATTACH = 2;

  public enum ProcessCloseConfirmation {ASK, TERMINATE, DISCONNECT}

  public static final String PROP_INACTIVE_TIMEOUT = "inactiveTimeout";
  public static final String PROP_SUPPORT_SCREEN_READERS = "supportScreenReaders";

  public static final String SCREEN_READERS_DETECTED_PROPERTY = "ide.support.screenreaders.detected";
  public static final String SUPPORT_SCREEN_READERS = "ide.support.screenreaders.enabled";
  private static final Boolean SUPPORT_SCREEN_READERS_OVERRIDDEN = getSupportScreenReadersOverridden();

  static final UINumericRange SAVE_FILES_AFTER_IDLE_SEC = new UINumericRange(15, 1, 300);

  private String myBrowserPath = BrowserUtil.getDefaultAlternativeBrowserPath();
  private boolean myShowTipsOnStartup = true;
  private boolean myReopenLastProject = true;
  private boolean mySupportScreenReaders = ObjectUtils.chooseNotNull(SUPPORT_SCREEN_READERS_OVERRIDDEN, Boolean.getBoolean(SCREEN_READERS_DETECTED_PROPERTY));
  private boolean mySyncOnFrameActivation = true;
  private boolean mySaveOnFrameDeactivation = true;
  private boolean myAutoSaveIfInactive = false;  // If true the IDE automatically saves files if it is inactive for some seconds
  private int myInactiveTimeout = 15; // Number of seconds of inactivity after which the IDE automatically saves all files
  private boolean myUseSafeWrite = true;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private boolean myUseDefaultBrowser = true;
  private boolean mySearchInBackground;
  private boolean myConfirmExit = true;
  private boolean myShowWelcomeScreen = !PlatformUtils.isDataGrip();
  private int myConfirmOpenNewProject = OPEN_PROJECT_ASK;
  private ProcessCloseConfirmation myProcessCloseConfirmation = ProcessCloseConfirmation.ASK;
  private String myDefaultProjectDirectory = "";

  private static final String CONFIGURED_PROPERTY = "GeneralSettings.initiallyConfigured";

  public static GeneralSettings getInstance() {
    return ApplicationManager.getApplication().getService(GeneralSettings.class);
  }

  public GeneralSettings() {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isHeadlessEnvironment()) {
      return;
    }

    if (PlatformUtils.isPyCharmEducational() || PlatformUtils.isRubyMine() || PlatformUtils.isWebStorm()) {
      PropertiesComponent propertyManager = PropertiesComponent.getInstance();
      if (!propertyManager.isValueSet(CONFIGURED_PROPERTY)) {
        propertyManager.setValue(CONFIGURED_PROPERTY, true);
        setShowTipsOnStartup(false);
      }
    }
  }

  public void addPropertyChangeListener(@NotNull String propertyName, @NotNull Disposable parentDisposable, @NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    Disposer.register(parentDisposable, () -> myPropertyChangeSupport.removePropertyChangeListener(propertyName, listener));
  }

  public String getBrowserPath() {
    return myBrowserPath;
  }

  public void setBrowserPath(String browserPath) {
    myBrowserPath = browserPath;
  }

  public boolean isShowTipsOnStartup() {
    return myShowTipsOnStartup;
  }

  public void setShowTipsOnStartup(boolean value) {
    myShowTipsOnStartup = value;
  }

  public boolean isReopenLastProject() {
    return myReopenLastProject;
  }

  public void setReopenLastProject(boolean reopenLastProject) {
    myReopenLastProject = reopenLastProject;
  }

  private static @Nullable Boolean getSupportScreenReadersOverridden() {
    String prop = System.getProperty(SUPPORT_SCREEN_READERS);
    return prop != null ? Boolean.parseBoolean(prop) : null;
  }

  public static boolean isSupportScreenReadersOverridden() {
    return SUPPORT_SCREEN_READERS_OVERRIDDEN != null;
  }

  public boolean isSupportScreenReaders() {
    return mySupportScreenReaders;
  }

  public void setSupportScreenReaders(boolean enabled) {
    boolean oldValue = mySupportScreenReaders;
    mySupportScreenReaders = enabled;
    myPropertyChangeSupport.firePropertyChange(
      PROP_SUPPORT_SCREEN_READERS, Boolean.valueOf(oldValue), Boolean.valueOf(enabled)
    );
  }

  public ProcessCloseConfirmation getProcessCloseConfirmation() {
    return myProcessCloseConfirmation;
  }

  public void setProcessCloseConfirmation(ProcessCloseConfirmation processCloseConfirmation) {
    myProcessCloseConfirmation = processCloseConfirmation;
  }

  @OptionTag("autoSyncFiles")
  public boolean isSyncOnFrameActivation() {
    return mySyncOnFrameActivation;
  }

  public void setSyncOnFrameActivation(boolean syncOnFrameActivation) {
    mySyncOnFrameActivation = syncOnFrameActivation;
  }

  @OptionTag("autoSaveFiles")
  public boolean isSaveOnFrameDeactivation() {
    return mySaveOnFrameDeactivation;
  }

  public void setSaveOnFrameDeactivation(boolean saveOnFrameDeactivation) {
    mySaveOnFrameDeactivation = saveOnFrameDeactivation;
  }

  /**
   * @return {@code true} if IDE saves all files after "idle" timeout.
   */
  public boolean isAutoSaveIfInactive(){
    return myAutoSaveIfInactive;
  }

  public void setAutoSaveIfInactive(boolean autoSaveIfInactive) {
    myAutoSaveIfInactive = autoSaveIfInactive;
  }

  /**
   * @return timeout in seconds after which IDE saves all files if there was no user activity.
   * The method always return positive (more then zero) value.
   */
  public int getInactiveTimeout(){
    return SAVE_FILES_AFTER_IDLE_SEC.fit(myInactiveTimeout);
  }

  public void setInactiveTimeout(int inactiveTimeoutSeconds) {
    int oldInactiveTimeout = myInactiveTimeout;

    myInactiveTimeout = SAVE_FILES_AFTER_IDLE_SEC.fit(inactiveTimeoutSeconds);
    myPropertyChangeSupport.firePropertyChange(
        PROP_INACTIVE_TIMEOUT, Integer.valueOf(oldInactiveTimeout), Integer.valueOf(myInactiveTimeout)
    );
  }

  public boolean isUseSafeWrite() {
    return myUseSafeWrite;
  }

  public void setUseSafeWrite(boolean value) {
    myUseSafeWrite = value;
  }

  @NotNull
  @Override
  public GeneralSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GeneralSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isUseDefaultBrowser() {
    return myUseDefaultBrowser;
  }

  public void setUseDefaultBrowser(boolean value) {
    myUseDefaultBrowser = value;
  }

  /**
   * @deprecated unused
   */
  @Transient
  @Deprecated
  public boolean isConfirmExtractFiles() {
    return true;
  }

  /**
   * @deprecated unused
   */
  @Deprecated
  public void setConfirmExtractFiles(@SuppressWarnings("unused") boolean value) { }

  public boolean isConfirmExit() {
    return myConfirmExit;
  }

  public void setConfirmExit(boolean confirmExit) {
    myConfirmExit = confirmExit;
  }

  public boolean isShowWelcomeScreen() {
    return myShowWelcomeScreen;
  }

  public void setShowWelcomeScreen(boolean show) {
    myShowWelcomeScreen = show;
  }

  @MagicConstant(intValues = {OPEN_PROJECT_ASK, OPEN_PROJECT_NEW_WINDOW, OPEN_PROJECT_SAME_WINDOW})
  @interface OpenNewProjectOption {}
  /**
   * @return
   * <ul>
   * <li>{@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW} if new project should be opened in new window
   * <li>{@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW} if new project should be opened in same window
   * <li>{@link GeneralSettings#OPEN_PROJECT_ASK} if a confirmation dialog should be shown
   * </ul>
   */
  @OpenNewProjectOption
  @OptionTag("confirmOpenNewProject2")
  @ReportValue
  public int getConfirmOpenNewProject() {
    return myConfirmOpenNewProject;
  }

  public void setConfirmOpenNewProject(@OpenNewProjectOption int confirmOpenNewProject) {
    myConfirmOpenNewProject = confirmOpenNewProject;
  }

  public boolean isSearchInBackground() {
    return mySearchInBackground;
  }

  public void setSearchInBackground(final boolean searchInBackground) {
    mySearchInBackground = searchInBackground;
  }

  @SystemDependent
  public String getDefaultProjectDirectory() {
    return myDefaultProjectDirectory;
  }

  public void setDefaultProjectDirectory(@SystemDependent String defaultProjectDirectory) {
    myDefaultProjectDirectory = defaultProjectDirectory;
  }
}