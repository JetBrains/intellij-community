/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User: anna
 * Date: 01-Dec-2005
 */
public class ProfileEx implements Profile {
  public String myName;
  public File myFile;
  private static final Logger LOG = Logger.getInstance("com.intellij.profile.ProfileEx");
  public boolean myLocal = true;

  public ProfileEx(String name) {
    myName = name;
  }

  public ProfileEx(final String name, final File file) {
    myName = name;
    myFile = file;
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public File getFile() {
    return myFile;
  }

  public void copyFrom(Profile profile) {
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

  public void setLocal(boolean isLocal) {
    myLocal = isLocal;
  }

  public boolean isLocal() {
    return myLocal;
  }

  public void setName(String name) {
    myName = name;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

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

  public int compareTo(final Object o) {
    if (o instanceof Profile){
      return getName().compareToIgnoreCase(((Profile)o).getName());
    }
    return 0;
  }
}
