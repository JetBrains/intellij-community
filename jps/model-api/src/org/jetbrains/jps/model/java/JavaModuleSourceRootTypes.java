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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

public class JavaModuleSourceRootTypes {
  public static final Set<JavaSourceRootType> SOURCES = ContainerUtil.newHashSet(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);
  public static final Set<JavaResourceRootType> RESOURCES = ContainerUtil.newHashSet(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> PRODUCTION = ContainerUtil.newHashSet(JavaSourceRootType.SOURCE, JavaResourceRootType.RESOURCE);
  public static final Set<? extends JpsModuleSourceRootType<?>> TESTS = ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE, JavaResourceRootType.TEST_RESOURCE);

  /**
   * @deprecated in order to check that a source root is a java-specific tests root, use {@link #TESTS} set, for arbitrary roots use
   * {@link JpsModuleSourceRootType#isForTests()} instead
   */
  @Deprecated
  public static boolean isTestSourceOrResource(@Nullable JpsModuleSourceRootType<?> type) {
    return JavaSourceRootType.TEST_SOURCE.equals(type) || JavaResourceRootType.TEST_RESOURCE.equals(type);
  }
}
