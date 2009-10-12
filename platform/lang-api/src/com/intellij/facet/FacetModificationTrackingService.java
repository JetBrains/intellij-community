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

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.ModificationTrackerListener;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public abstract class FacetModificationTrackingService {

  public static FacetModificationTrackingService getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, FacetModificationTrackingService.class);
  }

  public static FacetModificationTrackingService getInstance(@NotNull Facet facet) {
    return ModuleServiceManager.getService(facet.getModule(), FacetModificationTrackingService.class);
  }

  @NotNull
  public abstract ModificationTracker getFacetModificationTracker(@NotNull Facet facet);

  public abstract void incFacetModificationTracker(@NotNull Facet facet);

  public abstract <T extends Facet> void addModificationTrackerListener(final T facet, final ModificationTrackerListener<? super T> listener, final Disposable parent);

  public abstract void removeModificationTrackerListener(final Facet facet, final ModificationTrackerListener<?> listener);
}
