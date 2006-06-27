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
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ResourceUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class IntentionManagerSettings implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings");

  private Set<String> myIgnoredActions = new LinkedHashSet<String>();

  private Map<String,IntentionActionMetaData> myMetaData = new LinkedHashMap<String, IntentionActionMetaData>();
  private static final @NonNls String IGNORE_ACTION_TAG = "ignoreAction";
  private static final @NonNls String NAME_ATT = "name";

  private HashMap<String, ArrayList<String>> myWords2DescriptorsMap = new HashMap<String, ArrayList<String>>();

  static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  private SearchableOptionsRegistrar mySearchableOptionsRegistrar;

  public IntentionManagerSettings(@NotNull final SearchableOptionsRegistrar searchableOptionsRegistrar) {
    mySearchableOptionsRegistrar = searchableOptionsRegistrar;
  }

  public String getExternalFileName() {
    return "intentionSettings";
  }

  public static IntentionManagerSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(IntentionManagerSettings.class);
  }

  @NotNull
  public String getComponentName() {
    return "IntentionManagerSettings";
  }

  public void initComponent() { }

  public void registerIntentionMetaData(@NotNull IntentionAction intentionAction, @NotNull String[] category) {
    registerIntentionMetaData(intentionAction, category, intentionAction.getFamilyName());
  }
  public void registerIntentionMetaData(@NotNull IntentionAction intentionAction, @NotNull String[] category, @NotNull String descriptionDirectoryName) {
    registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), intentionAction.getClass().getClassLoader(), category, descriptionDirectoryName));
  }

  public void disposeComponent() {
  }

  public boolean isShowLightBulb(@NotNull IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  public void setShowLightBulb(@NotNull IntentionAction action, boolean show) {
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

  @NotNull public List<IntentionActionMetaData> getMetaData() {
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
    if (!myMetaData.containsKey(metaData.myFamily)){
      try {
        processMetaData(metaData);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    myMetaData.put(metaData.myFamily, metaData);
  }


  public void buildIndex(){
    try {
      final List<IntentionActionMetaData> list = getMetaData();
      for (IntentionActionMetaData metaData : list) {
        processMetaData(metaData);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void processMetaData(@NotNull final IntentionActionMetaData metaData) throws IOException {
    final URL description = metaData.getDescription();
    if (description != null) {
      @NonNls String descriptionText = ResourceUtil.loadText(description).toLowerCase();
      descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
      final Set<String> words = mySearchableOptionsRegistrar.getProcessedWords(descriptionText);
      words.addAll(mySearchableOptionsRegistrar.getProcessedWords(metaData.myFamily));
      for (String word : words) {
        ArrayList<String> descriptors = myWords2DescriptorsMap.get(word);
        if (descriptors == null) {
          descriptors = new ArrayList<String>();
          myWords2DescriptorsMap.put(word, descriptors);
        }
        descriptors.add(metaData.myFamily);
      }
    }
  }

  public ArrayList<String> getIntentionNames(final String filtString) {
    return myWords2DescriptorsMap.get(filtString);
  }

  public Set<String> getIntentionWords() {
    return myWords2DescriptorsMap.keySet();
  }

  public List<String> getFilteredIntentionNames(final String word) {
    return myWords2DescriptorsMap.get(word);
  }
}
