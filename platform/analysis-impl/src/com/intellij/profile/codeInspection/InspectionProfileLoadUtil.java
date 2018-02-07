/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.JdomKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class InspectionProfileLoadUtil {
  private static String getProfileName(@NotNull Path file, @NotNull Element element) {
    String name = null;
    for (Element option : element.getChildren("option")) {
      if ("myName".equals(option.getAttributeValue("name"))) {
        name = option.getAttributeValue("value");
      }
    }
    if (name == null) {
      //noinspection deprecation
      name = element.getAttributeValue("profile_name");
    }
    return name != null ? name : FileUtilRt.getNameWithoutExtension(file.getFileName().toString());
  }

  /**
   * @deprecated Please use load(file: Path, ...)
   */
  @NotNull
  @Deprecated
  public static InspectionProfileImpl load(@NotNull File file,
                                           @NotNull InspectionToolRegistrar registrar,
                                           @NotNull InspectionProfileManager profileManager) throws JDOMException, IOException, InvalidDataException {
    return load(file.toPath(), registrar, profileManager);
  }

  @NotNull
  public static InspectionProfileImpl load(@NotNull Path file,
                                           @NotNull InspectionToolRegistrar registrar,
                                           @NotNull InspectionProfileManager profileManager) throws JDOMException, IOException, InvalidDataException {
    Element element = JdomKt.loadElement(file);
    String profileName = getProfileName(file, element);
    return load(element, profileName, registrar, profileManager);
  }

  @NotNull
  public static InspectionProfileImpl load(@NotNull Element element,
                                           @NotNull String name,
                                           @NotNull Supplier<List<InspectionToolWrapper>> registrar,
                                           @NotNull InspectionProfileManager profileManager) throws JDOMException, IOException, InvalidDataException {
    InspectionProfileImpl profile = new InspectionProfileImpl(name, registrar,
                                                              (BaseInspectionProfileManager)profileManager);
    final Element profileElement = element.getChild("profile");
    if (profileElement != null) {
      element = profileElement;
    }
    profile.readExternal(element);
    return profile;
  }
}
