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
import com.intellij.profile.scope.ProfileScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public abstract class ProfileManager {

  @Nullable
  public static ProfileManager getProfileManager(String type) {
    final ProfileManager[] components = ApplicationManager.getApplication().getComponents(ProfileManager.class);
    for (ProfileManager manager : components) {
      if (manager.getProfileType().compareTo(type) == 0) {
        return manager;
      }
    }
    return null;
  }

  public static Set<String> getRegisteredProfileTypes() {
    final Set<String> result = new HashSet<String>();
    final ProfileManager[] components = ApplicationManager.getApplication().getComponents(ProfileManager.class);
    for (ProfileManager manager : components) {
      result.add(manager.getProfileType());
    }
    return result;
  }

  public abstract Profile createProfile();

  public abstract String getProfileType();

  public abstract void addProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void removeProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void fireProfileChanged(String profile);

  public abstract void fireProfileChanged(String oldProfile, String profile, ProfileScope scope);

  public abstract Collection<Profile> getProfiles();

  public abstract void setRootProfile(String rootProfile);

  public abstract String [] getAvailableProfileNames();

  @Nullable
  //profile was removed
  public abstract Profile getProfile(@NotNull String name);

  public abstract Profile getRootProfile();

  public abstract void deleteProfile(String profile);

  public abstract void addProfile(Profile profile);

  public abstract void readProfiles(Element element) throws InvalidDataException;

  public abstract void updateProfile(Profile profile);
}
