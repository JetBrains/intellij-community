// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.ide.ui.TopHitCache;
import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.Interner;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@State(name = "IntentionManagerSettings", storages = @Storage("intentionSettings.xml"))
public final class IntentionManagerSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(IntentionManagerSettings.class);

  private static final class MetaDataKey extends AbstractMap.SimpleImmutableEntry<String, String> {
    private static final Interner<String> ourInterner = Interner.createWeakInterner();

    private MetaDataKey(String @NotNull [] categoryNames, @NotNull String familyName) {
      super(String.join(":", categoryNames), ourInterner.intern(familyName));
    }
  }

  private final Set<String> myIgnoredActions = Collections.synchronizedSet(new LinkedHashSet<>());

  private final Map<MetaDataKey, IntentionActionMetaData> myMetaData; // guarded by this
  private final Map<IntentionActionBean, MetaDataKey> myExtensionMapping; // guarded by this

  @NonNls private static final String IGNORE_ACTION_TAG = "ignoreAction";
  @NonNls private static final String NAME_ATT = "name";
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  public IntentionManagerSettings() {
    int size = IntentionManagerImpl.EP_INTENTION_ACTIONS.getPoint().size();
    myMetaData = new LinkedHashMap<>(size);
    myExtensionMapping = new HashMap<>(size);

    IntentionManagerImpl.EP_INTENTION_ACTIONS.forEachExtensionSafe(extension -> registerMetaDataForEp(extension));

    IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(new ExtensionPointListener<IntentionActionBean>() {
      @Override
      public void extensionAdded(@NotNull IntentionActionBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        // on each plugin load/unload SearchableOptionsRegistrarImpl drops the cache, so, it will be recomputed later on demand
        registerMetaDataForEp(extension);
        TopHitCache topHitCache = ApplicationManager.getApplication().getServiceIfCreated(TopHitCache.class);
        if (topHitCache != null) {
          topHitCache.invalidateCachedOptions(IntentionsOptionsTopHitProvider.class);
        }
      }

      @Override
      public void extensionRemoved(@NotNull IntentionActionBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        String[] categories = extension.getCategories();
        if (categories == null) return;
        unregisterMetaDataForEP(extension);
        TopHitCache topHitCache = ApplicationManager.getApplication().getServiceIfCreated(TopHitCache.class);
        if (topHitCache != null) {
          topHitCache.invalidateCachedOptions(IntentionsOptionsTopHitProvider.class);
        }
      }
    }, null);
  }

  private void registerMetaDataForEp(@NotNull IntentionActionBean extension) {
    String[] categories = extension.getCategories();
    if (categories == null) {
      return;
    }

    IntentionActionWrapper instance = new IntentionActionWrapper(extension);
    String descriptionDirectoryName = extension.getDescriptionDirectoryName();
    if (descriptionDirectoryName == null) {
      descriptionDirectoryName = instance.getDescriptionDirectoryName();
    }

    try {
      IntentionActionMetaData metaData = new IntentionActionMetaData(instance, extension.getLoaderForClass(), categories, descriptionDirectoryName);
      MetaDataKey key = new MetaDataKey(metaData.myCategory, metaData.getFamily());
      //noinspection SynchronizeOnThis
      synchronized (this) {
        myMetaData.put(key, metaData);
        myExtensionMapping.put(extension, key);
      }
    }
    catch (ExtensionNotApplicableException ignore) {
    }
  }

  public static @NotNull IntentionManagerSettings getInstance() {
    return ApplicationManager.getApplication().getService(IntentionManagerSettings.class);
  }

  void registerIntentionMetaData(@NotNull IntentionAction intentionAction,
                                 String @NotNull [] category,
                                 @NotNull String descriptionDirectoryName) {
    IntentionActionMetaData metaData = new IntentionActionMetaData(intentionAction, getClassLoader(intentionAction), category, descriptionDirectoryName);
    MetaDataKey key = new MetaDataKey(metaData.myCategory, metaData.getFamily());
    synchronized (this) {
      // not added as searchable option - this method is deprecated and intentionAction extension point must be used instead
      myMetaData.put(key, metaData);
    }
  }

  private static ClassLoader getClassLoader(@NotNull IntentionAction intentionAction) {
    return intentionAction instanceof IntentionActionWrapper
           ? ((IntentionActionWrapper)intentionAction).getImplementationClassLoader()
           : intentionAction.getClass().getClassLoader();
  }

  public boolean isShowLightBulb(@NotNull IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  @Override
  public void loadState(@NotNull Element element) {
    myIgnoredActions.clear();
    for (Element e : element.getChildren(IGNORE_ACTION_TAG)) {
      myIgnoredActions.add(e.getAttributeValue(NAME_ATT));
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    for (String name : myIgnoredActions) {
      element.addContent(new Element(IGNORE_ACTION_TAG).setAttribute(NAME_ATT, name));
    }
    return element;
  }

  @NotNull
  public synchronized List<IntentionActionMetaData> getMetaData() {
    return new ArrayList<>(myMetaData.values());
  }

  public boolean isEnabled(@NotNull IntentionActionMetaData metaData) {
    return !myIgnoredActions.contains(getFamilyName(metaData));
  }

  private static String getFamilyName(@NotNull IntentionActionMetaData metaData) {
    return String.join("/", metaData.myCategory) + '/' + metaData.getAction().getFamilyName();
  }

  private static String getFamilyName(@NotNull IntentionAction action) {
    return action instanceof IntentionActionWrapper ? ((IntentionActionWrapper)action).getFullFamilyName() : action.getFamilyName();
  }

  public void setEnabled(@NotNull IntentionActionMetaData metaData, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(getFamilyName(metaData));
    }
    else {
      myIgnoredActions.add(getFamilyName(metaData));
    }
  }

  public boolean isEnabled(@NotNull IntentionAction action) {
    return !myIgnoredActions.contains(getFamilyName(action));
  }

  public void setEnabled(@NotNull IntentionAction action, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(getFamilyName(action));
    }
    else {
      myIgnoredActions.add(getFamilyName(action));
    }
  }

  private static void processMetaData(@NotNull IntentionActionMetaData metaData, @NotNull SearchableOptionProcessor processor) {
    try {
      String descriptionText = Strings.toLowerCase(metaData.getDescription().getText());
      descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
      String displayName = IntentionSettingsConfigurable.getDisplayNameText();
      String configurableId = IntentionSettingsConfigurable.HELP_ID;
      String family = metaData.getFamily();
      processor.addOptions(descriptionText, family, family, configurableId, displayName, false);
      processor.addOptions(family, family, family, configurableId, displayName, true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  synchronized void unregisterMetaData(@NotNull IntentionAction intentionAction) {
    for (Map.Entry<MetaDataKey, IntentionActionMetaData> entry : myMetaData.entrySet()) {
      if (entry.getValue().getAction() == intentionAction) {
        myMetaData.remove(entry.getKey());
        break;
      }
    }
  }

  private synchronized void unregisterMetaDataForEP(IntentionActionBean extension) {
    MetaDataKey key = myExtensionMapping.remove(extension);
    if (key != null) {
      myMetaData.remove(key);
    }
  }

  private static final class IntentionSearchableOptionContributor extends SearchableOptionContributor {
    @Override
    public void processOptions(@NotNull SearchableOptionProcessor processor) {
      for (IntentionActionMetaData metaData : getInstance().getMetaData()) {
        processMetaData(metaData, processor);
      }
    }
  }
}
