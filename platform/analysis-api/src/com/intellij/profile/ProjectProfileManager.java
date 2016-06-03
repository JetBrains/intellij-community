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

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.profile.DefaultProjectProfileManager.PROFILE;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class ProjectProfileManager implements ProfileManager {
  public abstract String getProfileName();

  public abstract String getProjectProfile();

  public abstract void setProjectProfile(@Nullable String projectProfile);

  @NotNull
  public static Element serializeProfile(@NotNull Profile profile) {
    Element result = new Element(PROFILE);
    profile.writeExternal(result);
    return result;
  }
}
