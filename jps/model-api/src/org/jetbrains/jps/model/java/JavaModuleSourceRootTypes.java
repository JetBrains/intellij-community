/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

/**
 * @author nik
 */
public class JavaModuleSourceRootTypes {
  public static final Set<JavaSourceRootType> SOURCES = ContainerUtilRt.newHashSet(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);
  public static final Set<JavaResourceRootType> RESOURCES = ContainerUtilRt.newHashSet(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> PRODUCTION = ContainerUtilRt.newHashSet(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> TESTS = ContainerUtilRt.newHashSet(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE);

  public static boolean isTestSourceOrResource(@Nullable JpsModuleSourceRootType<?> type) {
    return JavaSourceRootType.TEST_SOURCE.equals(type) || JavaResourceRootType.TEST_RESOURCE.equals(type);
  }
}
