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
package com.intellij.profile;

import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.project.ProjectKt;
import com.intellij.util.xmlb.SmartSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 01-Dec-2005
 */
public abstract class ProfileEx implements Profile {
  public static final String SCOPE = "scope";
  public static final String NAME = "name";
  public static final String PROFILE = "profile";

  private final SmartSerializer mySerializer;

  @NotNull
  protected String myName;

  protected ProfileManager myProfileManager;

  private boolean myIsProjectLevel;

  public ProfileEx(@NotNull String name) {
    this(name, SmartSerializer.skipEmptySerializer());
  }

  protected ProfileEx(@NotNull String name, @NotNull SmartSerializer serializer) {
    myName = name;
    mySerializer = serializer;
  }

  @Override
  @NotNull
  // ugly name to preserve compatibility
  @OptionTag("myName")
  public String getName() {
    return myName;
  }

  @Override
  @Transient
  public boolean isProjectLevel() {
    return myIsProjectLevel;
  }

  @Override
  public void setProjectLevel(boolean isProjectLevel) {
    myIsProjectLevel = isProjectLevel;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  @Transient
  public ProfileManager getProfileManager() {
    return myProfileManager;
  }

  @Override
  public void setProfileManager(@NotNull ProfileManager profileManager) {
    myProfileManager = profileManager;
  }

  public void readExternal(Element element) {
    mySerializer.readExternal(this, element);
  }

  public void writeExternal(@NotNull Element element) {
    mySerializer.writeExternal(this, element, false);
  }

  public boolean equals(Object o) {
    return this == o || o instanceof ProfileEx && myName.equals(((ProfileEx)o).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(@NotNull Object o) {
    if (o instanceof Profile) {
      return getName().compareToIgnoreCase(((Profile)o).getName());
    }
    return 0;
  }

  public final void copyFrom(@NotNull ProfileEx profile) {
    readExternal(profile.writeScheme());
  }

  @NotNull
  public Element writeScheme() {
    Element element = new Element(PROFILE);
    if (isProjectLevel()) {
      element.setAttribute("version", "1.0");
    }
    writeExternal(element);

    if (isProjectLevel() && ProjectKt.isDirectoryBased(((ProjectInspectionProfileManager)myProfileManager).getProject())) {
      return new Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(element);
    }
    return element;
  }
}
