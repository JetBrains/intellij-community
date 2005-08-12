/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;
import java.util.List;

public class GeneralSettings implements NamedJDOMExternalizable, ApplicationComponent {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String PROP_INACTIVE_TIMEOUT = "inactiveTimeout";
  private static final int DEFAULT_INACTIVE_TIMEOUT = 15;

  private String myBrowserPath;
  private boolean myShowTipsOnStartup = true;
  private int myLastTip = 0;
  private boolean myShowOccupiedMemory = false;
  private boolean myReopenLastProject = true;
  private boolean mySyncOnFrameActivation = true;
  private boolean mySaveOnFrameDeactivation = true;
  private boolean myAutoSaveIfInactive = false;  // If true the IDEA automatically saves files if it is inactive for some seconds
  private int myInactiveTimeout; // Number of seconds of inactivity after which IDEA automatically saves all files
  private String myCharsetName;
  private boolean myUseUTFGuessing = true;
  private PropertyChangeSupport myPropertyChangeSupport;
  private boolean myUseDefaultBrowser = true;
  private String myLastProjectLocation;
  private boolean myUseCyclicBuffer;
  private int myCyclicBufferSize = 1024*1024; //1Mb
  private boolean myConfirmExit = true;

  public static GeneralSettings getInstance(){
    return ApplicationManager.getApplication().getComponent(GeneralSettings.class);
  }

  /** Invoked by reflection */
  public GeneralSettings() {
    myInactiveTimeout=DEFAULT_INACTIVE_TIMEOUT;
    myCharsetName="System Default";

    if (SystemInfo.isWindows) {
      //noinspection HardCodedStringLiteral
      myBrowserPath = "C:\\Program Files\\Internet Explorer\\IExplore.exe";
    }
    else if (SystemInfo.isMac) {
      //noinspection HardCodedStringLiteral
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
        PROP_INACTIVE_TIMEOUT,
        new Integer(oldInactiveTimeout),
        new Integer(inactiveTimeout)
    );
  }

  public boolean isUseUTFGuessing() {
    return myUseUTFGuessing;
  }

  public void setUseUTFGuessing(boolean useUTFGuessing) {
    myUseUTFGuessing = useUTFGuessing;
  }

  public String getCharsetName() {
    return myCharsetName;
  }

  public void setCharsetName(String charsetName) {
    myCharsetName = charsetName;
  }

  //todo use DefaultExternalizer
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element parentNode) {
    List children = parentNode.getChildren("option");
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      Element element = (Element)iterator.next();

      String name = element.getAttributeValue("name");
      String value = element.getAttributeValue("value");

      if ("browserPath".equals(name)) {
        myBrowserPath = value;
      }
      if ("lastTip".equals(name)) {
        try {
          myLastTip = new Integer(value).intValue();
        }
        catch (NumberFormatException ex) {
          myLastTip = 0;
        }
      }
      if ("showTipsOnStartup".equals(name)) {
        try {
          myShowTipsOnStartup = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myShowTipsOnStartup = true;
        }
      }
      if ("showOccupiedMemory".equals(name)) {
        try {
          myShowOccupiedMemory = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myShowOccupiedMemory = false;
        }
      }
      if ("reopenLastProject".equals(name)) {
        try {
          myReopenLastProject = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myReopenLastProject = true;
        }
      }
      if ("autoSyncFiles".equals(name)) {
        try {
          mySyncOnFrameActivation = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          mySyncOnFrameActivation = true;
        }
      }
      if ("autoSaveFiles".equals(name)) {
        try {
          mySaveOnFrameDeactivation = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          mySaveOnFrameDeactivation = true;
        }
      }
      if("autoSaveIfInactive".equals(name) && value!= null){
        myAutoSaveIfInactive=Boolean.valueOf(value).booleanValue();
      }
      if("inactiveTimeout".equals(name)){
        try {
          int inactiveTimeout=Integer.parseInt(value);
          if(inactiveTimeout>0){
            myInactiveTimeout = inactiveTimeout;
          }
        } catch(Exception ignored) { }
      }
      if ("charset".equals(name)) {
        try {
          if (!"Default".equals(value)) {
            myCharsetName = value;
          } else {
            myCharsetName = "System Default";
          }
        }
        catch (Exception ex) {
          myCharsetName = "System Default";
        }
      }
      if ("UTFGuessing".equals(name)) {
        try {
          myUseUTFGuessing = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myUseUTFGuessing = true;
        }
      }

      if ("useDefaultBrowser".equals(name)){
        try {
          myUseDefaultBrowser = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myUseDefaultBrowser = true;
        }
      }

      if ("useCyclicBuffer".equals(name)){
        try {
          myUseCyclicBuffer = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myUseCyclicBuffer = false;
        }
      }

      if ("confirmExit".equals(name)){
        try {
          myConfirmExit = Boolean.valueOf(value).booleanValue();
        }
        catch (Exception ex) {
          myConfirmExit = false;
        }
      }

      if ("cyclicBufferSize".equals(name)){
        try {
          myCyclicBufferSize = Integer.parseInt(value);
        }
        catch (Exception ex) {
          myCyclicBufferSize = 0;
        }
      }

      if ("lastProjectLocation".equals(name)){
        try {
          myLastProjectLocation = value;
        }
        catch (Exception ex) {
          myLastProjectLocation = null;
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element parentNode) {
    if (myBrowserPath != null) {
      Element element = new Element("option");
      element.setAttribute("name", "browserPath");
      element.setAttribute("value", myBrowserPath);
      parentNode.addContent(element);
    }

    Element optionElement = new Element("option");
    optionElement.setAttribute("name", "lastTip");
    optionElement.setAttribute("value", Integer.toString(myLastTip));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "showTipsOnStartup");
    optionElement.setAttribute("value", Boolean.toString(myShowTipsOnStartup));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "showOccupiedMemory");
    optionElement.setAttribute("value", Boolean.toString(myShowOccupiedMemory));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "reopenLastProject");
    optionElement.setAttribute("value", Boolean.toString(myReopenLastProject));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "autoSyncFiles");
    optionElement.setAttribute("value", Boolean.toString(mySyncOnFrameActivation));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "autoSaveFiles");
    optionElement.setAttribute("value", Boolean.toString(mySaveOnFrameDeactivation));
    parentNode.addContent(optionElement);

    // AutoSave if inactive

    optionElement = new Element("option");
    optionElement.setAttribute("name","autoSaveIfInactive");
    optionElement.setAttribute("value",(myAutoSaveIfInactive?Boolean.TRUE:Boolean.FALSE).toString());
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name","inactiveTimeout");
    optionElement.setAttribute("value",Integer.toString(myInactiveTimeout));
    parentNode.addContent(optionElement);

    //

    optionElement = new Element("option");
    optionElement.setAttribute("name", "charset");
    optionElement.setAttribute("value", myCharsetName);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "UTFGuessing");
    optionElement.setAttribute("value", Boolean.toString(myUseUTFGuessing));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "useDefaultBrowser");
    optionElement.setAttribute("value", Boolean.toString(myUseDefaultBrowser));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "useCyclicBuffer");
    optionElement.setAttribute("value", Boolean.toString(myUseCyclicBuffer));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "cyclicBufferSize");
    optionElement.setAttribute("value", Integer.toString(myCyclicBufferSize));
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "confirmExit");
    optionElement.setAttribute("value", Boolean.toString(myConfirmExit));
    parentNode.addContent(optionElement);

    if (myLastProjectLocation != null) {
      optionElement = new Element("option");
      optionElement.setAttribute("name", "lastProjectLocation");
      optionElement.setAttribute("value", myLastProjectLocation);
      parentNode.addContent(optionElement);
    }
  }

  public String getExternalFileName() {
    return "ide.general";
  }

  public String getComponentName() {
    return "GeneralSettings";
  }

  public boolean isUseDefaultBrowser() {
    return myUseDefaultBrowser;
  }

  public void setUseDefaultBrowser(boolean value) {
    myUseDefaultBrowser = value;
  }

  public boolean isUseCyclicBuffer() {
    return myUseCyclicBuffer;
  }

  public void setUseCyclicBuffer(final boolean useCyclicBuffer) {
    myUseCyclicBuffer = useCyclicBuffer;
  }

  public boolean isConfirmExit() {
    return myConfirmExit;
  }

  public void setConfirmExit(boolean confirmExit) {
    myConfirmExit = confirmExit;
  }

  public int getCyclicBufferSize() {
    return myCyclicBufferSize;
  }

  public void setCyclicBufferSize(final int cyclicBufferSize) {
    myCyclicBufferSize = cyclicBufferSize;
  }
}