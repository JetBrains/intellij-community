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

import com.intellij.openapi.Disposable;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public abstract class ApplicationProfileManager implements ProfileManager{
  public abstract Profile createProfile();

  public abstract void addProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void addProfileChangeListener(ProfileChangeAdapter listener, Disposable parentDisposable);

  public abstract void removeProfileChangeListener(ProfileChangeAdapter listener);

  public abstract void fireProfileChanged(Profile profile);

  public abstract void fireProfileChanged(Profile oldProfile, Profile profile, @Nullable NamedScope scope);

  public abstract void setRootProfile(String rootProfile);

  public abstract Profile getRootProfile();

  public abstract void addProfile(Profile profile);

  @Nullable
  public NamedScopesHolder getScopesManager() {
    return null;
  }
}
