// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.ex.ApplicationInspectionProfileManager;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileKt;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
    String profile = ApplicationInspectionProfileManager.getInstanceImpl().getRootProfileName();
    if (!InspectionProfileKt.DEFAULT_PROFILE_NAME.equals(profile)) {
      element.setAttribute("profile", profile);
    }
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    XmlSerializer.deserializeInto(this, state);
    ApplicationInspectionProfileManager inspectionProfileManager = ApplicationInspectionProfileManager.getInstanceImpl();
    inspectionProfileManager.getConverter().storeEditorHighlightingProfile(state,
                                                                           new InspectionProfileImpl(InspectionProfileConvertor.OLD_HIGHTLIGHTING_SETTINGS_PROFILE));
    inspectionProfileManager.setRootProfile(StringUtil.notNullize(state.getAttributeValue("profile"), "Default"));
  }
}
