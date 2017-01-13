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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SchemeListItem<T extends Scheme> {

  public static final String EMPTY_NAME_MESSAGE = "The name must not be empty";
  public static final String NAME_ALREADY_EXISTS_MESSAGE = "The name already exists";

  public enum SchemeLevel {
    IDE_Only, IDE, Project
  }
  
  private @Nullable T myScheme;

  public SchemeListItem(@Nullable T scheme) {
    myScheme = scheme;
  }

  @Nullable
  public String getSchemeName() {
    return myScheme != null ? myScheme.getName() : null;
  }

  @Nullable
  public T getScheme() {
    return myScheme;
  }

  @NotNull
  public String getPresentableText() {
    return myScheme != null ? SchemeManager.getDisplayName(myScheme) : "";
  }

  public boolean isSeparator() {
    return false;
  }
  
  public abstract boolean isDuplicateAvailable();
  
  public abstract boolean isResetAvailable();
  
  public abstract boolean isDeleteAvailable();
  
  public abstract SchemeLevel getSchemeLevel();
  
  public abstract boolean isRenameAvailable();
  
  @Nullable
  public String validateSchemeName(@NotNull String name) {
    if (name.isEmpty()) {
      return EMPTY_NAME_MESSAGE;
    }
    return null;
  }
}
