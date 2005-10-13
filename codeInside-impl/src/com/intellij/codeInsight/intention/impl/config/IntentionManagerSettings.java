/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 23, 2002
 * Time: 8:15:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class IntentionManagerSettings implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings");

  private Set<String> myIgnoredActions = new LinkedHashSet<String>();

  private Map<String,IntentionActionMetaData> myMetaData = new LinkedHashMap<String, IntentionActionMetaData>();
  private static final @NonNls String IGNORE_ACTION_TAG = "ignoreAction";
  private static final @NonNls String NAME_ATT = "name";


  public String getExternalFileName() {
    return "intentionSettings";
  }

  public static IntentionManagerSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(IntentionManagerSettings.class);
  }

  public String getComponentName() {
    return "IntentionManagerSettings";
  }

  public void initComponent() { }

  public void registerIntentionMetaData(IntentionAction intentionAction, String[] category) {
    registerIntentionMetaData(intentionAction, category, intentionAction.getFamilyName());
  }
  public void registerIntentionMetaData(IntentionAction intentionAction, String[] category, String descriptionDirectoryName) {
    registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), intentionAction.getClass().getClassLoader(), category, descriptionDirectoryName));
  }

  public void disposeComponent() {
  }

  public boolean isShowLightBulb(IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  public void setShowLightBulb(IntentionAction action, boolean show) {
    if (show) {
      myIgnoredActions.remove(action.getFamilyName());
    }
    else {
      myIgnoredActions.add(action.getFamilyName());
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    myIgnoredActions.clear();
    List children = element.getChildren(IGNORE_ACTION_TAG);
    for (final Object aChildren : children) {
      Element e = (Element)aChildren;
      myIgnoredActions.add(e.getAttributeValue(NAME_ATT));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (String name : myIgnoredActions) {
      element.addContent(new Element(IGNORE_ACTION_TAG).setAttribute(NAME_ATT, name));
    }
  }

  public List<IntentionActionMetaData> getMetaData() {
    return new ArrayList<IntentionActionMetaData>(myMetaData.values());
  }

  public boolean isEnabled(String family) {
    return !myIgnoredActions.contains(family);
  }
  public void setEnabled(String family, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(family);
    }
    else {
      myIgnoredActions.add(family);
    }
  }

  public void registerMetaData(IntentionActionMetaData metaData) {
    //LOG.assertTrue(!myMetaData.containsKey(metaData.myFamily), "Action '"+metaData.myFamily+"' already registered");
    myMetaData.put(metaData.myFamily, metaData);
  }
}
