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

  public static GeneralSettings getInstance(){
    return ApplicationManager.getApplication().getComponent(GeneralSettings.class);
  }

  /** Invoked by reflection */
  public GeneralSettings() {
    myInactiveTimeout=DEFAULT_INACTIVE_TIMEOUT;
    myCharsetName="System Default";

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
          myShowTipsOnStartup = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          myShowTipsOnStartup = true;
        }
      }
      if ("showOccupiedMemory".equals(name)) {
        try {
          myShowOccupiedMemory = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          myShowOccupiedMemory = false;
        }
      }
      if ("reopenLastProject".equals(name)) {
        try {
          myReopenLastProject = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          myReopenLastProject = true;
        }
      }
      if ("autoSyncFiles".equals(name)) {
        try {
          mySyncOnFrameActivation = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          mySyncOnFrameActivation = true;
        }
      }
      if ("autoSaveFiles".equals(name)) {
        try {
          mySaveOnFrameDeactivation = new Boolean(value).booleanValue();
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
          myUseUTFGuessing = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          myUseUTFGuessing = true;
        }
      }

      if ("useDefaultBrowser".equals(name)){
        try {
          myUseDefaultBrowser = new Boolean(value).booleanValue();
        }
        catch (Exception ex) {
          myUseDefaultBrowser = true;
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

  public void writeExternal(Element parentNode) {
    if (myBrowserPath != null) {
      Element element = new Element("option");
      element.setAttribute("name", "browserPath");
      element.setAttribute("value", myBrowserPath);
      parentNode.addContent(element);
    }

    Element optionElement = new Element("option");
    optionElement.setAttribute("name", "lastTip");
    optionElement.setAttribute("value", "" + myLastTip);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "showTipsOnStartup");
    optionElement.setAttribute("value", "" + myShowTipsOnStartup);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "showOccupiedMemory");
    optionElement.setAttribute("value", "" + myShowOccupiedMemory);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "reopenLastProject");
    optionElement.setAttribute("value", "" + myReopenLastProject);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "autoSyncFiles");
    optionElement.setAttribute("value", "" + mySyncOnFrameActivation);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "autoSaveFiles");
    optionElement.setAttribute("value", "" + mySaveOnFrameDeactivation);
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
    optionElement.setAttribute("value", "" + myUseUTFGuessing);
    parentNode.addContent(optionElement);

    optionElement = new Element("option");
    optionElement.setAttribute("name", "useDefaultBrowser");
    optionElement.setAttribute("value", "" + myUseDefaultBrowser);
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
}