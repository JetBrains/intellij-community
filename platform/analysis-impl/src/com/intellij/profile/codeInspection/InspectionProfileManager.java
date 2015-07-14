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
package com.intellij.profile.codeInspection;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.ProfileEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public abstract class InspectionProfileManager extends ApplicationProfileManager implements SeverityProvider {
  @NonNls public static final String INSPECTION_DIR = "inspection";

  private final List<ProfileChangeAdapter> myProfileChangeAdapters = ContainerUtil.createLockFreeCopyOnWriteList();

  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProfileManager");

  public static InspectionProfileManager getInstance() {
    return ServiceManager.getService(InspectionProfileManager.class);
  }

  public InspectionProfileManager() {
  }

  protected abstract void initProfiles();

  public abstract Profile loadProfile(@NotNull String path) throws IOException, JDOMException;

  @Override
  public void addProfileChangeListener(@NotNull final ProfileChangeAdapter listener) {
    myProfileChangeAdapters.add(listener);
  }

  @Override
  public void addProfileChangeListener(@NotNull ProfileChangeAdapter listener, @NotNull Disposable parentDisposable) {
    ContainerUtil.add(listener, myProfileChangeAdapters, parentDisposable);
  }

  @Override
  public void removeProfileChangeListener(@NotNull final ProfileChangeAdapter listener) {
    myProfileChangeAdapters.remove(listener);
  }

  @Override
  public void fireProfileChanged(final Profile profile) {
    if (profile instanceof ProfileEx) {
      ((ProfileEx)profile).profileChanged();
    }
    for (ProfileChangeAdapter adapter : myProfileChangeAdapters) {
      adapter.profileChanged(profile);
    }
  }

  @Override
  public void fireProfileChanged(final Profile oldProfile, final Profile profile, final NamedScope scope) {
    for (ProfileChangeAdapter adapter : myProfileChangeAdapters) {
      adapter.profileActivated(oldProfile, profile);
    }
  }

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }
}
