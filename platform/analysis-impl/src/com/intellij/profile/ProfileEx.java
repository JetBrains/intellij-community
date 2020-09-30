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
package com.intellij.profile;

import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.SmartSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

public abstract class ProfileEx implements Comparable, ExternalizableScheme {
  public static final String SCOPE = "scope";
  public static final String NAME = "name";
  public static final String PROFILE = "profile";

  protected final SmartSerializer mySerializer;

  protected @NotNull @NlsSafe String myName;

  public ProfileEx(@NotNull String name) {
    myName = name;
    mySerializer = SmartSerializer.skipEmptySerializer();
  }

  @Override
  @NotNull
  // ugly name to preserve compatibility
  @OptionTag("myName")
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ProfileEx && myName.equals(((ProfileEx)o).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(@NotNull Object o) {
    if (o instanceof ProfileEx) {
      return getName().compareToIgnoreCase(((ProfileEx)o).getName());
    }
    return 0;
  }
}
