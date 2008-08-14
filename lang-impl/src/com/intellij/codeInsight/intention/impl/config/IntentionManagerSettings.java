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
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
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

  private class MetaDataKey {
    @NotNull private String categoryNames;
    @NotNull private String familyName;

    private MetaDataKey(@NotNull String[] categoryNames, @NotNull final String familyName) {
      this.categoryNames = StringUtil.join(categoryNames, ":");
      this.familyName = familyName;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MetaDataKey that = (MetaDataKey)o;

      if (!categoryNames.equals(that.categoryNames)) return false;
      if (!familyName.equals(that.familyName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = categoryNames.hashCode();
      result = 31 * result + familyName.hashCode();
      return result;
    }
  }
  

  private final Set<String> myIgnoredActions = new LinkedHashSet<String>();

  private final Map<MetaDataKey, IntentionActionMetaData> myMetaData = new LinkedHashMap<MetaDataKey, IntentionActionMetaData>();
  private static final @NonNls String IGNORE_ACTION_TAG = "ignoreAction";
  private static final @NonNls String NAME_ATT = "name";
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");


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

  public synchronized void registerIntentionMetaData(@NotNull IntentionAction intentionAction, @NotNull String[] category, @NotNull String descriptionDirectoryName) {
    registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), getClassLoader(intentionAction), category, descriptionDirectoryName));
  }

  private static ClassLoader getClassLoader(final IntentionAction intentionAction) {
    if (intentionAction instanceof IntentionActionWrapper) {
      return getClassLoader(((IntentionActionWrapper)intentionAction).getDelegate());
    }
    else {
      return intentionAction.getClass().getClassLoader();
    }
  }

  public void registerIntentionMetaData(final IntentionAction intentionAction, final String[] category, final String descriptionDirectoryName,
                                        final ClassLoader classLoader) {
    registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), classLoader, category, descriptionDirectoryName));
  }

  public void disposeComponent() {
  }

  public synchronized boolean isShowLightBulb(@NotNull IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
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

  @NotNull public synchronized List<IntentionActionMetaData> getMetaData() {
    IntentionManager.getInstance(); // TODO: Hack to make IntentionManager actually register metadata here. Dependencies between IntentionManager and IntentionManagerSettings should be revised.
    return new ArrayList<IntentionActionMetaData>(myMetaData.values());
  }

  public synchronized boolean isEnabled(IntentionActionMetaData metaData) {
    return !myIgnoredActions.contains(getFamilyName(metaData));
  }

  private String getFamilyName(final IntentionActionMetaData metaData) {
    return StringUtil.join(metaData.myCategory, "/") + "/" + metaData.myFamily;
  }

  private String getFamilyName(final IntentionAction action) {
    if (action instanceof IntentionActionWrapper) {
      return ((IntentionActionWrapper)action).getFullFamilyName();
    }
    else {
      return action.getFamilyName();
    }
  }

  public synchronized void setEnabled(IntentionActionMetaData metaData, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(getFamilyName(metaData));
    }
    else {
      myIgnoredActions.add(getFamilyName(metaData));
    }
  }

  public synchronized boolean isEnabled(IntentionAction action) {
    return !myIgnoredActions.contains(getFamilyName(action));
  }
  public synchronized void setEnabled(IntentionAction action, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(getFamilyName(action));
    }
    else {
      myIgnoredActions.add(getFamilyName(action));
    }
  }

  private void registerMetaData(IntentionActionMetaData metaData) {
    MetaDataKey key = new MetaDataKey(metaData.myCategory, metaData.myFamily);
    //LOG.assertTrue(!myMetaData.containsKey(metaData.myFamily), "Action '"+metaData.myFamily+"' already registered");
    if (!myMetaData.containsKey(key)){
      try {
        processMetaData(metaData);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    myMetaData.put(key, metaData);
  }

  private static void processMetaData(@NotNull final IntentionActionMetaData metaData) throws IOException {
    final URL description = metaData.getDescription();
    if (description != null) {
      SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
      if (registrar == null) return;
      @NonNls String descriptionText = ResourceUtil.loadText(description).toLowerCase();
      descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
      final Set<String> words = registrar.getProcessedWordsWithoutStemming(descriptionText);
      words.addAll(registrar.getProcessedWords(metaData.myFamily));
      for (String word : words) {
        registrar.addOption(word, metaData.myFamily, metaData.myFamily, IntentionSettingsConfigurable.HELP_ID, IntentionSettingsConfigurable.DISPLAY_NAME);
      }
    }
  }
}
