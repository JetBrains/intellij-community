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
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathUtil;
import com.intellij.util.ThreeState;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JCiPExternalLibraryResolver extends ExternalLibraryResolver {
  private static final ExternalLibraryDescriptor JDCIP_LIBRARY_DESCRIPTOR =
    new ExternalLibraryDescriptor("net.jcip", "jcip-annotations") {
      @NotNull
      @Override
      public List<String> getLibraryClassesRoots() {
        return Collections.singletonList(PathUtil.getJarPathForClass(GuardedBy.class));
      }

      @Override
      public String getPresentableName() {
        return "jcip-annotations.jar";
      }
    };

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (JCiPUtil.isJCiPAnnotation(shortClassName) && isAnnotation == ThreeState.YES) {
      return new ExternalClassResolveResult("net.jcip.annotations." + shortClassName, JDCIP_LIBRARY_DESCRIPTOR);
    }
    return null;
  }

  @Nullable
  @Override
  public ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("net.jcip.annotations")) {
      return JDCIP_LIBRARY_DESCRIPTOR;
    }
    return null;
  }
}
