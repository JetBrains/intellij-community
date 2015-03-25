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
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class MockPackageFacadeBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    //todo[nik] this is a temporary solution until all team members have Kotlin plugin installed
    try {
      Class<?> aClass = Class.forName("org.jetbrains.jps.builders.java.MockPackageFacadeGenerator");
      return Collections.singletonList((ModuleLevelBuilder)aClass.newInstance());
    }
    catch (ClassNotFoundException e) {
      return Collections.emptyList();
    }
    catch (NoClassDefFoundError e) {
      return Collections.emptyList();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
