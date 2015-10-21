/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.options.Scheme;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 20-Nov-2005
 */
public interface Profile extends Comparable, Scheme {
  void copyFrom(@NotNull Profile profile);

  void setLocal(boolean isLocal);

  @Deprecated
  /**
   * @deprecated Use !{@link #isProjectLevel()}
   */
  boolean isLocal();

  boolean isProjectLevel();

  void setProjectLevel(boolean isProjectLevel);

  void setName(@NotNull String name);

  @Override
  @NotNull
  String getName();

  void setProfileManager(@NotNull ProfileManager profileManager);

  @NotNull
  ProfileManager getProfileManager();

  void readExternal(Element element);

  void writeExternal(Element element);
}
