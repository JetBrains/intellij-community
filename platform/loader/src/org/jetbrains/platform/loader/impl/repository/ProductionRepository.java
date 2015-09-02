/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.impl.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.repository.PlatformRepository;

import java.io.File;

/**
 * @author nik
 */
public class ProductionRepository extends PlatformRepositoryBase implements PlatformRepository {
  private final File myIdeHome;

  public ProductionRepository(@NotNull File ideHome) {
    super(loadModulesFromZip(ideHome));
    myIdeHome = ideHome;
  }

  @Override
  public String toString() {
    return "Production Repository [" + myIdeHome.getAbsolutePath() + "]";
  }
}
