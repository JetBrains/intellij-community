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
package com.intellij.profile;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 01-Dec-2005
 */
public abstract class ProfileEx implements Profile {
  // public for JDOMExternalizable
  @NotNull
  public String myName;
  private static final Logger LOG = Logger.getInstance("com.intellij.profile.ProfileEx");
  public boolean myLocal = true;
  protected ProfileManager myProfileManager;
  @NonNls public static final String SCOPE = "scope";
  public static final String NAME = "name";

  public ProfileEx(@NotNull String name) {
    setName(name);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public void copyFrom(@NotNull Profile profile) {
    try {
      @NonNls final Element config = new Element("config");
      profile.writeExternal(config);
      readExternal(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @Override
  public void setLocal(boolean isLocal) {
    myLocal = isLocal;
  }

  @Override
  public boolean isLocal() {
    return myLocal;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public void setProfileManager(@NotNull ProfileManager profileManager) {
    myProfileManager = profileManager;
  }

  @Override
  @NotNull
  public ProfileManager getProfileManager() {
    return myProfileManager;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void profileChanged() {}

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ProfileEx)) return false;

    final ProfileEx profileEx = (ProfileEx)o;

    if (!myName.equals(profileEx.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(final Object o) {
    if (o instanceof Profile){
      return getName().compareToIgnoreCase(((Profile)o).getName());
    }
    return 0;
  }

  public void convert(@NotNull Element element, @NotNull Project project) {}
}
