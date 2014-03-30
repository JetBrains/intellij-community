/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsElementFactory {
  public static JpsElementFactory getInstance() {
    return JpsServiceManager.getInstance().getService(JpsElementFactory.class);
  }

  public abstract JpsModel createModel();

  public abstract <P extends JpsElement> JpsModule createModule(@NotNull String name, @NotNull JpsModuleType<P> type, @NotNull P properties);

  public abstract <P extends JpsElement> JpsTypedLibrary<P> createLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties);

  public abstract <P extends JpsElement> JpsTypedLibrary<JpsSdk<P>> createSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString,
                                                                              @NotNull JpsSdkType<P> type, @NotNull P properties);

  @NotNull
  public abstract <P extends JpsElement> JpsModuleSourceRoot createModuleSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type, @NotNull P properties);

  @NotNull
  public abstract JpsModuleReference createModuleReference(@NotNull String moduleName);

  @NotNull
  public abstract JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                             @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference);

  @NotNull
  public abstract <P extends JpsElement> JpsSdkReference<P> createSdkReference(@NotNull String sdkName,
                                                                                                @NotNull JpsSdkType<P> sdkType);

  @NotNull
  public abstract JpsElementReference<JpsProject> createProjectReference();

  @NotNull
  public abstract JpsElementReference<JpsGlobal> createGlobalReference();

  @NotNull
  public abstract JpsDummyElement createDummyElement();

  @NotNull
  public abstract <D> JpsSimpleElement<D> createSimpleElement(@NotNull D data);
}
