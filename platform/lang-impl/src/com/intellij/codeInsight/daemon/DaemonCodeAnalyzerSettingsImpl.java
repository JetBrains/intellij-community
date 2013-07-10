/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@State(
  name="DaemonCodeAnalyzerSettings",
  storages= {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml"
    )}
)
public class DaemonCodeAnalyzerSettingsImpl extends DaemonCodeAnalyzerSettings implements PersistentStateComponent<Element>, Cloneable, ExportableComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings");
  @NonNls private static final String ROOT_TAG = "root";
  @NonNls private static final String PROFILE_ATT = "profile";
  private final InspectionProfileManagerImpl myManager;

  public DaemonCodeAnalyzerSettingsImpl(InspectionProfileManager manager) {
    myManager = (InspectionProfileManagerImpl)manager;
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("editor.codeinsight")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return DaemonBundle.message("error.highlighting.settings");
  }

  @Override
  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    try {
      Element rootNew = new Element(ROOT_TAG);
      writeExternal(rootNew);
      Element rootOld = new Element(ROOT_TAG);
      ((DaemonCodeAnalyzerSettingsImpl)oldSettings).writeExternal(rootOld);

      return !JDOMUtil.areElementsEqual(rootOld, rootNew);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }

    return false;
  }

  @Override
  public DaemonCodeAnalyzerSettingsImpl clone() {
    DaemonCodeAnalyzerSettingsImpl settings = new DaemonCodeAnalyzerSettingsImpl(myManager);
    settings.AUTOREPARSE_DELAY = AUTOREPARSE_DELAY;
    settings.SHOW_ADD_IMPORT_HINTS = SHOW_ADD_IMPORT_HINTS;
    settings.SHOW_METHOD_SEPARATORS = SHOW_METHOD_SEPARATORS;
    settings.NO_AUTO_IMPORT_PATTERN = NO_AUTO_IMPORT_PATTERN;
    settings.SHOW_SMALL_ICONS_IN_GUTTER = SHOW_SMALL_ICONS_IN_GUTTER;
    return settings;
  }

  @Override
  public Element getState() {
    Element e = new Element("state");
    try {
      writeExternal(e);
    }
    catch (WriteExternalException ex) {
      LOG.error(ex);
    }
    return e;
  }

  @Override
  public void loadState(final Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    myManager.getConverter().storeEditorHighlightingProfile(element,
                                                            new InspectionProfileImpl(
                                                              InspectionProfileConvertor.OLD_HIGHTLIGHTING_SETTINGS_PROFILE));
    myManager.setRootProfile(element.getAttributeValue(PROFILE_ATT));
  }

  private void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    element.setAttribute(PROFILE_ATT, myManager.getRootProfile().getName());
  }
}
