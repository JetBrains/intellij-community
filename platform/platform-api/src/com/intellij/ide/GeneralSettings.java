/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

public class GeneralSettings implements NamedJDOMExternalizable, ExportableApplicationComponent {
  @NonNls private static final String OPTION_INACTIVE_TIMEOUT = "inactiveTimeout";
  @NonNls public static final String PROP_INACTIVE_TIMEOUT = OPTION_INACTIVE_TIMEOUT;
  private static final int DEFAULT_INACTIVE_TIMEOUT = 15;

  @NonNls private String myBrowserPath;
  private boolean myShowTipsOnStartup = true;
  private int myLastTip = 0;
  private boolean myShowOccupiedMemory = false;
  private boolean myReopenLastProject = true;
  private boolean mySyncOnFrameActivation = true;
  private boolean mySaveOnFrameDeactivation = true;
  private boolean myAutoSaveIfInactive = false;  // If true the IDEA automatically saves files if it is inactive for some seconds
  private int myInactiveTimeout; // Number of seconds of inactivity after which IDEA automatically saves all files
  private final PropertyChangeSupport myPropertyChangeSupport;
  private boolean myUseDefaultBrowser = true;
  private boolean myConfirmExtractFiles = true;
  private String myLastProjectLocation;
  private boolean mySearchInBackground;
  private boolean myConfirmExit = true;
  private int myConfirmOpenNewProject = -1;
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String OPTION_BROWSER_PATH = "browserPath";
  @NonNls private static final String OPTION_LAST_TIP = "lastTip";
  @NonNls private static final String OPTION_SHOW_TIPS_ON_STARTUP = "showTipsOnStartup";
  @NonNls private static final String OPTION_SHOW_OCCUPIED_MEMORY = "showOccupiedMemory";
  @NonNls private static final String OPTION_REOPEN_LAST_PROJECT = "reopenLastProject";
  @NonNls private static final String OPTION_AUTO_SYNC_FILES = "autoSyncFiles";
  @NonNls private static final String OPTION_AUTO_SAVE_FILES = "autoSaveFiles";
  @NonNls private static final String OPTION_AUTO_SAVE_IF_INACTIVE = "autoSaveIfInactive";
  @Deprecated
  @NonNls private static final String OPTION_CHARSET = "charset";
  @Deprecated
  @NonNls private static final String OPTION_UTFGUESSING = "UTFGuessing";

  @NonNls private static final String OPTION_USE_DEFAULT_BROWSER = "useDefaultBrowser";
  @NonNls private static final String OPTION_CONFIRM_EXTRACT_FILES = "confirmExtractFiles";
  @NonNls private static final String OPTION_USE_CYCLIC_BUFFER = "useCyclicBuffer";
  @NonNls private static final String OPTION_SEARCH_IN_BACKGROUND = "searchInBackground";
  @NonNls private static final String OPTION_CONFIRM_EXIT = "confirmExit";
  @NonNls private static final String OPTION_CONFIRM_OPEN_NEW_PROJECT = "confirmOpenNewProject";
  @NonNls private static final String OPTION_CYCLIC_BUFFER_SIZE = "cyclicBufferSize";
  @NonNls private static final String OPTION_LAST_PROJECT_LOCATION = "lastProjectLocation";
  @Deprecated
  private Charset myCharset;
  @Deprecated
  private boolean myUseUTFGuessing;
  @Deprecated
  private boolean oldCharsetSettingsHaveBeenRead;

  public static GeneralSettings getInstance(){
    return ApplicationManager.getApplication().getComponent(GeneralSettings.class);
  }

  public GeneralSettings() {
    myInactiveTimeout=DEFAULT_INACTIVE_TIMEOUT;

    if (SystemInfo.isWindows) {
      myBrowserPath = "C:\\Program Files\\Internet Explorer\\IExplore.exe";
    }
    else if (SystemInfo.isMac) {
      myBrowserPath = "open";
    }
    else {
      myBrowserPath = "";
    }

    myPropertyChangeSupport = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void initComponent() { }

  public void disposeComponent() { }

  public String getBrowserPath() {
    return myBrowserPath;
  }

  /**
   * @return a path pointing to a directory where the last project was created or null if not available
   */
  public String getLastProjectLocation() {
    return myLastProjectLocation;
  }

  public void setLastProjectLocation(String lastProjectLocation) {
    myLastProjectLocation = lastProjectLocation;
  }

  public void setBrowserPath(String browserPath) {
    myBrowserPath = browserPath;
  }

  public boolean showTipsOnStartup() {
    return myShowTipsOnStartup;
  }

  public void setShowTipsOnStartup(boolean b) {
    myShowTipsOnStartup = b;
  }

  public int getLastTip() {
    return myLastTip;
  }

  public void setLastTip(int i) {
    myLastTip = i;
  }

  public boolean isShowOccupiedMemory() {
    return myShowOccupiedMemory;
  }

  public boolean isReopenLastProject() {
    return myReopenLastProject;
  }

  public void setReopenLastProject(boolean reopenLastProject) {
    myReopenLastProject = reopenLastProject;
  }

  public boolean isSyncOnFrameActivation() {
    return mySyncOnFrameActivation;
  }

  public void setSyncOnFrameActivation(boolean syncOnFrameActivation) {
    mySyncOnFrameActivation = syncOnFrameActivation;
  }

  public boolean isSaveOnFrameDeactivation() {
    return mySaveOnFrameDeactivation;
  }

  public void setSaveOnFrameDeactivation(boolean saveOnFrameDeactivation) {
    mySaveOnFrameDeactivation = saveOnFrameDeactivation;
  }

  /**
   * @return <code>true</code> if IDEA saves all files after "idle" timeout.
   */
  public boolean isAutoSaveIfInactive(){
    return myAutoSaveIfInactive;
  }

  public void setAutoSaveIfInactive(boolean autoSaveIfInactive) {
    myAutoSaveIfInactive = autoSaveIfInactive;
  }

  /**
   * @return timeout in seconds after which IDEA saves all files if there was no user activity.
   * The method always return non positive (more then zero) value.
   */
  public int getInactiveTimeout(){
    return myInactiveTimeout;
  }

  public void setInactiveTimeout(int inactiveTimeout) {
    int oldInactiveTimeout = myInactiveTimeout;

    myInactiveTimeout = inactiveTimeout;
    myPropertyChangeSupport.firePropertyChange(
        PROP_INACTIVE_TIMEOUT, Integer.valueOf(oldInactiveTimeout), Integer.valueOf(inactiveTimeout)
    );
  }

  //todo use DefaultExternalizer
  public void readExternal(Element parentNode) {
    List children = parentNode.getChildren(ELEMENT_OPTION);
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;

      String name = element.getAttributeValue(ATTRIBUTE_NAME);
      String value = element.getAttributeValue(ATTRIBUTE_VALUE);

      if (OPTION_BROWSER_PATH.equals(name)) {
        myBrowserPath = value;
      }
      if (OPTION_LAST_TIP.equals(name)) {
        try {
          myLastTip = Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
          myLastTip = 0;
        }
      }
      if (OPTION_SHOW_TIPS_ON_STARTUP.equals(name)) {
        try {
          myShowTipsOnStartup = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myShowTipsOnStartup = true;
        }
      }
      if (OPTION_SHOW_OCCUPIED_MEMORY.equals(name)) {
        try {
          myShowOccupiedMemory = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myShowOccupiedMemory = false;
        }
      }
      if (OPTION_REOPEN_LAST_PROJECT.equals(name)) {
        try {
          myReopenLastProject = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myReopenLastProject = true;
        }
      }
      if (OPTION_AUTO_SYNC_FILES.equals(name)) {
        try {
          mySyncOnFrameActivation = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          mySyncOnFrameActivation = true;
        }
      }
      if (OPTION_AUTO_SAVE_FILES.equals(name)) {
        try {
          mySaveOnFrameDeactivation = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          mySaveOnFrameDeactivation = true;
        }
      }
      if (OPTION_AUTO_SAVE_IF_INACTIVE.equals(name) && value != null) {
        myAutoSaveIfInactive = Boolean.valueOf(value).booleanValue();
      }
      if (OPTION_INACTIVE_TIMEOUT.equals(name)) {
        try {
          int inactiveTimeout = Integer.parseInt(value);
          if (inactiveTimeout > 0) {
            myInactiveTimeout = inactiveTimeout;
          }
        }
        catch (Exception ignored) {
        }
      }
      if (OPTION_CHARSET.equals(name)) {
        //for migration
        myCharset = CharsetToolkit.forName(value);
        oldCharsetSettingsHaveBeenRead = true;
      }
      if (OPTION_UTFGUESSING.equals(name)) {
        myUseUTFGuessing = Boolean.valueOf(value).booleanValue();
        oldCharsetSettingsHaveBeenRead = true;
      }

      if (OPTION_USE_DEFAULT_BROWSER.equals(name)) {
        try {
          myUseDefaultBrowser = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myUseDefaultBrowser = true;
        }
      }

      if (OPTION_CONFIRM_EXTRACT_FILES.equals(name)) {
        try {
          myConfirmExtractFiles = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myConfirmExtractFiles = true;
        }
      }

      if (OPTION_SEARCH_IN_BACKGROUND.equals(name)) {
        try {
          mySearchInBackground = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          mySearchInBackground = false;
        }
      }

      if (OPTION_CONFIRM_EXIT.equals(name)) {
        try {
          myConfirmExit = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myConfirmExit = false;
        }
      }

      if (OPTION_CONFIRM_OPEN_NEW_PROJECT.equals(name)) {
        try {
          myConfirmOpenNewProject = Integer.valueOf(value).intValue();
        }
        catch (Exception ex) {
          myConfirmOpenNewProject = -1;
        }
      }

      if (OPTION_LAST_PROJECT_LOCATION.equals(name)) {
        try {
          myLastProjectLocation = value;
        }
        catch (Exception ex) {
          myLastProjectLocation = null;
        }
      }
    }
  }

  public void writeExternal(Element parentNode) {
    if (myBrowserPath != null) {
      Element element = new Element(ELEMENT_OPTION);
      element.setAttribute(ATTRIBUTE_NAME, OPTION_BROWSER_PATH);
      element.setAttribute(ATTRIBUTE_VALUE, myBrowserPath);
      parentNode.addContent(element);
    }

    Element optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_LAST_TIP);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Integer.toString(myLastTip));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_SHOW_TIPS_ON_STARTUP);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myShowTipsOnStartup));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_SHOW_OCCUPIED_MEMORY);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myShowOccupiedMemory));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_REOPEN_LAST_PROJECT);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myReopenLastProject));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_AUTO_SYNC_FILES);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(mySyncOnFrameActivation));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_AUTO_SAVE_FILES);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(mySaveOnFrameDeactivation));
    parentNode.addContent(optionElement);

    // AutoSave if inactive

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME,OPTION_AUTO_SAVE_IF_INACTIVE);
    optionElement.setAttribute(ATTRIBUTE_VALUE,(myAutoSaveIfInactive?Boolean.TRUE:Boolean.FALSE).toString());
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME,OPTION_INACTIVE_TIMEOUT);
    optionElement.setAttribute(ATTRIBUTE_VALUE,Integer.toString(myInactiveTimeout));
    parentNode.addContent(optionElement);

    //

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_USE_DEFAULT_BROWSER);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myUseDefaultBrowser));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_CONFIRM_EXTRACT_FILES);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myConfirmExtractFiles));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_SEARCH_IN_BACKGROUND);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(mySearchInBackground));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_CONFIRM_EXIT);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Boolean.toString(myConfirmExit));
    parentNode.addContent(optionElement);

    optionElement = new Element(ELEMENT_OPTION);
    optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_CONFIRM_OPEN_NEW_PROJECT);
    optionElement.setAttribute(ATTRIBUTE_VALUE, Integer.toString(myConfirmOpenNewProject));
    parentNode.addContent(optionElement);

    if (myLastProjectLocation != null) {
      optionElement = new Element(ELEMENT_OPTION);
      optionElement.setAttribute(ATTRIBUTE_NAME, OPTION_LAST_PROJECT_LOCATION);
      optionElement.setAttribute(ATTRIBUTE_VALUE, myLastProjectLocation);
      parentNode.addContent(optionElement);
    }
  }

  public String getExternalFileName() {
    return "ide.general";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("general.settings");
  }

  @NotNull
  public String getComponentName() {
    return "GeneralSettings";
  }

  public boolean isUseDefaultBrowser() {
    return myUseDefaultBrowser;
  }

  public void setUseDefaultBrowser(boolean value) {
    myUseDefaultBrowser = value;
  }

  public boolean isConfirmExtractFiles() {
    return myConfirmExtractFiles;
  }

  public void setConfirmExtractFiles(boolean value) {
    myConfirmExtractFiles = value;
  }

  public boolean isConfirmExit() {
    return myConfirmExit;
  }

  public void setConfirmExit(boolean confirmExit) {
    myConfirmExit = confirmExit;
  }

  /**
   * @return
   * <ul>
   * <li>0 if new project should be opened in new window
   * <li>1 if new project should be opened in same window
   * <li>-1 if a confirmation dialog should be shown
   * </ul>
   */
  public int getConfirmOpenNewProject() {
    return myConfirmOpenNewProject;
  }

  public void setConfirmOpenNewProject(int confirmOpenNewProject) {
    myConfirmOpenNewProject = confirmOpenNewProject;
  }

  public boolean isSearchInBackground() {
    return mySearchInBackground;
  }

  public void setSearchInBackground(final boolean searchInBackground) {
    mySearchInBackground = searchInBackground;
  }

  // returns true if something has been migrated
  public boolean migrateCharsetSettingsTo(EncodingManager encodingProjectManager) {
    if (oldCharsetSettingsHaveBeenRead) {
      encodingProjectManager.setEncoding(null, myCharset);
      encodingProjectManager.setUseUTFGuessing(null, myUseUTFGuessing);
    }
    return oldCharsetSettingsHaveBeenRead;
  }
}