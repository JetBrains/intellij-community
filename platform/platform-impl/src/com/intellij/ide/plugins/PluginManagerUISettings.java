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
package com.intellij.ide.plugins;

import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
@State(
  name = "PluginManagerConfigurable",
  storages = {
  @Storage(
    file = "$APP_CONFIG$/other.xml")
    }
)
public class PluginManagerUISettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerUISettings");
  
  public int AVAILABLE_SORT_MODE = 0;
  public boolean AVAILABLE_SORT_BY_STATUS = false;
  public JDOMExternalizableStringList myOutdatedPlugins = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList myInstalledPlugins = new JDOMExternalizableStringList();

  @NonNls private static final String AVAILABLE_PROPORTIONS = "available-proportions";

  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsDataImpl();
  private final SplitterProportionsData myAvailableSplitterProportionsData = new SplitterProportionsDataImpl();

  public static PluginManagerUISettings getInstance() {
    return ServiceManager.getService(PluginManagerUISettings.class);
  }

  public Element getState() {
    Element element = new Element("state");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
      mySplitterProportionsData.writeExternal(element);

      final Element availableProportions = new Element(AVAILABLE_PROPORTIONS);
      myAvailableSplitterProportionsData.writeExternal(availableProportions);
      element.addContent(availableProportions);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    return element;
  }

  public void loadState(final Element element) {
    try {
      DefaultJDOMExternalizer.readExternal(this, element);
      mySplitterProportionsData.readExternal(element);
      final Element availableProportionsElement = element.getChild(AVAILABLE_PROPORTIONS);
      if (availableProportionsElement != null) {
        myAvailableSplitterProportionsData.readExternal(availableProportionsElement);
      }
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
  }

  public SplitterProportionsData getSplitterProportionsData() {
    return mySplitterProportionsData;
  }
  
  public SplitterProportionsData getAvailableSplitterProportionsData() {
    return myAvailableSplitterProportionsData;
  }
}
