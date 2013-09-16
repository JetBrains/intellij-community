/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
@State(
  name = "PluginManagerConfigurable",
  storages = {
  @Storage(
    file = StoragePathMacros.APP_CONFIG + "/plugin_ui.xml")
  }
)
public class PluginManagerUISettings implements PersistentStateComponent<Element>, PerformInBackgroundOption {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerUISettings");
  private static final SkipDefaultValuesSerializationFilters FILTERS = new SkipDefaultValuesSerializationFilters();

  public int AVAILABLE_SORT_COLUMN_ORDER = SortOrder.ASCENDING.ordinal();

  public int AVAILABLE_SORT_MODE = 0;
  public boolean AVAILABLE_SORT_BY_STATUS = false;
  public boolean INSTALLED_SORT_BY_STATUS = false;
  public boolean UPDATE_IN_BACKGROUND = false;
  public JDOMExternalizableStringList myOutdatedPlugins = new JDOMExternalizableStringList();

  private JDOMExternalizableStringList myInstalledPlugins = new JDOMExternalizableStringList();

  @NonNls private static final String AVAILABLE_PROPORTIONS = "available-proportions";

  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsDataImpl();
  private final SplitterProportionsData myAvailableSplitterProportionsData = new SplitterProportionsDataImpl();

  public JDOMExternalizableStringList getInstalledPlugins() {
    return myInstalledPlugins;
  }

  public static PluginManagerUISettings getInstance() {
    return ServiceManager.getService(PluginManagerUISettings.class);
  }

  public Element getState() {
    Element element = new Element("state");
    XmlSerializer.serializeInto(this, element, FILTERS);
    XmlSerializer.serializeInto(mySplitterProportionsData, element, FILTERS);

    final Element availableProportions = new Element(AVAILABLE_PROPORTIONS);
    XmlSerializer.serializeInto(myAvailableSplitterProportionsData, availableProportions, FILTERS);
    element.addContent(availableProportions);
    return element;
  }

  public void loadState(final Element element) {
    XmlSerializer.deserializeInto(this, element);
    XmlSerializer.deserializeInto(mySplitterProportionsData, element);
    final Element availableProportionsElement = element.getChild(AVAILABLE_PROPORTIONS);
    if (availableProportionsElement != null) {
      XmlSerializer.deserializeInto(myAvailableSplitterProportionsData, availableProportionsElement);
    }
  }

  public SplitterProportionsData getSplitterProportionsData() {
    return mySplitterProportionsData;
  }
  
  public SplitterProportionsData getAvailableSplitterProportionsData() {
    return myAvailableSplitterProportionsData;
  }

  @Override
  public boolean shouldStartInBackground() {
    return UPDATE_IN_BACKGROUND;
  }

  @Override
  public void processSentToBackground() {
    UPDATE_IN_BACKGROUND = true;
  }
}
