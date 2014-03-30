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
package com.intellij.profile.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class InspectionProfileLoadUtil {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls public static final String PROFILE_TAG = "profile";

  private static String getProfileName(@NotNull File file, @NotNull Document document) {
    String name = getRootElementAttribute(PROFILE_NAME_TAG, document);
    if (name != null) return name;
    return FileUtil.getNameWithoutExtension(file);
  }

  private static String getRootElementAttribute(@NotNull Document document, @NonNls String name) {
    Element root = document.getRootElement();
    return root.getAttributeValue(name);
  }

  @Nullable
  private static String getRootElementAttribute(@NonNls String name, final Document doc) {
    return getRootElementAttribute(doc, name);
  }

  @NotNull
  public static String getProfileName(@NotNull Document document) {
    String name = getRootElementAttribute(document, PROFILE_NAME_TAG);
    if (name != null) return name;
    return "unnamed";
  }

  @NotNull
  public static Profile load(@NotNull File file,
                             @NotNull InspectionToolRegistrar registrar,
                             @NotNull ProfileManager profileManager) throws JDOMException, IOException, InvalidDataException {
    Document document = JDOMUtil.loadDocument(file);
    InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file, document), registrar, profileManager);
    Element rootElement = document.getRootElement();
    final Element profileElement = rootElement.getChild(PROFILE_TAG);
    if (profileElement != null) {
      rootElement = profileElement;
    }
    profile.readExternal(rootElement);
    return profile;
  }
}
