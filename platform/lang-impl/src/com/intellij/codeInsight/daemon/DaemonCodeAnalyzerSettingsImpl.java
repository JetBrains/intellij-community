/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileManagerImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;

@State(
  name = "DaemonCodeAnalyzerSettings",
  storages = @Storage("editor.codeinsight.xml")
)
public class DaemonCodeAnalyzerSettingsImpl extends DaemonCodeAnalyzerSettings implements PersistentStateComponent<Element>, Cloneable {
  @Override
  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    return !JDOMUtil.areElementsEqual(((DaemonCodeAnalyzerSettingsImpl)oldSettings).getState(), getState());
  }

  @Override
  public DaemonCodeAnalyzerSettingsImpl clone() {
    DaemonCodeAnalyzerSettingsImpl settings = new DaemonCodeAnalyzerSettingsImpl();
    settings.AUTOREPARSE_DELAY = AUTOREPARSE_DELAY;
    settings.myShowAddImportHints = myShowAddImportHints;
    settings.SHOW_METHOD_SEPARATORS = SHOW_METHOD_SEPARATORS;
    settings.NO_AUTO_IMPORT_PATTERN = NO_AUTO_IMPORT_PATTERN;
    settings.SHOW_SMALL_ICONS_IN_GUTTER = SHOW_SMALL_ICONS_IN_GUTTER;
    return settings;
  }

  @Override
  public Element getState() {
    Element element = XmlSerializer.serialize(this, new SkipDefaultsSerializationFilter());
    String profile = InspectionProfileManagerImpl.getInstanceImpl().getRootProfileName();
    if (!InspectionProfileImpl.DEFAULT_PROFILE_NAME.equals(profile)) {
      element.setAttribute("profile", profile);
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    XmlSerializer.deserializeInto(this, state);
    InspectionProfileManagerImpl inspectionProfileManager = InspectionProfileManagerImpl.getInstanceImpl();
    inspectionProfileManager.getConverter().storeEditorHighlightingProfile(state,
                                                                           new InspectionProfileImpl(InspectionProfileConvertor.OLD_HIGHTLIGHTING_SETTINGS_PROFILE));
    inspectionProfileManager.setRootProfile(StringUtil.notNullize(state.getAttributeValue("profile"), "Default"));
  }
}
