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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.scope.ProfileScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public abstract class ProfileFactory {

  @Nullable
  public static ProfileFactory getProfileFactory(String type) {
    final ProfileFactory[] components = ApplicationManager.getApplication().getComponents(ProfileFactory.class);
    for (ProfileFactory factory : components) {
      if (factory.getProfileType().compareTo(type) == 0) {
        return factory;
      }
    }
    return null;
  }

  public static Set<String> getRegisteredProfileTypes() {
    final Set<String> result = new HashSet<String>();
    final ProfileFactory[] components = ApplicationManager.getApplication().getComponents(ProfileFactory.class);
    for (ProfileFactory factory : components) {
      result.add(factory.getProfileType());
    }
    return result;
  }

  public abstract Profile createProfile();

  public abstract String getProfileType();

  public abstract void addProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void removeProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void fireProfileChanged(Profile profile);

  public abstract void fireProfileChanged(Profile oldProfile, Profile profile, ProfileScope scope);

  public abstract List<Profile> getLocalProfiles();

  public abstract void setRootProfile(String rootProfile);

  public abstract List<Profile> getProjectProfiles();

  @Nullable
  //profile was removed
  public abstract Profile getProfile(@NotNull String name);

  public abstract Profile getRootProfile();

  public abstract void deleteProfile(Profile profile);

  public abstract void addProfile(Profile profile);

  public abstract void readProfiles(Element element) throws InvalidDataException;

  public abstract void writeProfiles(Element element, boolean isLocal) throws WriteExternalException;


}
