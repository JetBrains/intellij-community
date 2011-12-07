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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@State(
  name = "IntentionManagerSettings",
  storages = {
    @Storage(
      file = "$APP_CONFIG$/intentionSettings.xml"
    )}
)
public class IntentionManagerSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings");
  private static final Alarm ourRegisterMetaDataAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private class MetaDataKey {
    @NotNull private final String categoryNames;
    @NotNull private final String familyName;

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
  @NonNls private static final String IGNORE_ACTION_TAG = "ignoreAction";
  @NonNls private static final String NAME_ATT = "name";
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");


  public static IntentionManagerSettings getInstance() {
    return ServiceManager.getService(IntentionManagerSettings.class);
  }

  public void registerIntentionMetaData(@NotNull IntentionAction intentionAction, @NotNull String[] category, @NotNull String descriptionDirectoryName) {
    registerMetaData(new IntentionActionMetaData(intentionAction, getClassLoader(intentionAction), category, descriptionDirectoryName));
  }

  private static ClassLoader getClassLoader(final IntentionAction intentionAction) {
    return intentionAction instanceof IntentionActionWrapper
           ? ((IntentionActionWrapper)intentionAction).getImplementationClassLoader()
           : intentionAction.getClass().getClassLoader();
  }

  public void registerIntentionMetaData(final IntentionAction intentionAction, final String[] category, final String descriptionDirectoryName,
                                        final ClassLoader classLoader) {
    registerMetaData(new IntentionActionMetaData(intentionAction, classLoader, category, descriptionDirectoryName));
  }

  public synchronized boolean isShowLightBulb(@NotNull IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  public void loadState(Element element) {
    myIgnoredActions.clear();
    List children = element.getChildren(IGNORE_ACTION_TAG);
    for (final Object aChildren : children) {
      Element e = (Element)aChildren;
      myIgnoredActions.add(e.getAttributeValue(NAME_ATT));
    }
  }

  public Element getState() {
    Element element = new Element("state");
    for (String name : myIgnoredActions) {
      element.addContent(new Element(IGNORE_ACTION_TAG).setAttribute(NAME_ATT, name));
    }
    return element;
  }

  @NotNull public synchronized List<IntentionActionMetaData> getMetaData() {
    IntentionManager.getInstance(); // TODO: Hack to make IntentionManager actually register metadata here. Dependencies between IntentionManager and IntentionManagerSettings should be revised.
    return new ArrayList<IntentionActionMetaData>(myMetaData.values());
  }

  public synchronized boolean isEnabled(IntentionActionMetaData metaData) {
    return !myIgnoredActions.contains(getFamilyName(metaData));
  }

  private static String getFamilyName(final IntentionActionMetaData metaData) {
    return StringUtil.join(metaData.myCategory, "/") + "/" + metaData.getFamily();
  }

  private static String getFamilyName(final IntentionAction action) {
    return action instanceof IntentionActionWrapper ? ((IntentionActionWrapper)action).getFullFamilyName() : action.getFamilyName();
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

  public synchronized void registerMetaData(IntentionActionMetaData metaData) {
    MetaDataKey key = new MetaDataKey(metaData.myCategory, metaData.getFamily());
    //LOG.assertTrue(!myMetaData.containsKey(metaData.myFamily), "Action '"+metaData.myFamily+"' already registered");
    if (!myMetaData.containsKey(key)){
      processMetaData(metaData);
    }
    myMetaData.put(key, metaData);
  }

  private static synchronized void processMetaData(@NotNull final IntentionActionMetaData metaData) {
    final Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) return;

    final TextDescriptor description = metaData.getDescription();
    ourRegisterMetaDataAlarm.addRequest(new Runnable(){
      public void run() {
        try {
          SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
          if (registrar == null) return;
          @NonNls String descriptionText = description.getText().toLowerCase();
          descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
          final Set<String> words = registrar.getProcessedWordsWithoutStemming(descriptionText);
          words.addAll(registrar.getProcessedWords(metaData.getFamily()));
          for (String word : words) {
            registrar.addOption(word, metaData.getFamily(), metaData.getFamily(), IntentionSettingsConfigurable.HELP_ID, IntentionSettingsConfigurable.DISPLAY_NAME);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }, 0);
  }

  public synchronized void unregisterMetaData(IntentionAction intentionAction) {
    for (Map.Entry<MetaDataKey, IntentionActionMetaData> entry : myMetaData.entrySet()) {
      if (entry.getValue().getAction() == intentionAction) {
        myMetaData.remove(entry.getKey());
        break;
      }
    }
  }
}
