/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework;

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkTypeEx extends FrameworkType {
  public static final ExtensionPointName<FrameworkTypeEx> EP_NAME = ExtensionPointName.create("com.intellij.framework.type");

  protected FrameworkTypeEx(@NotNull String id) {
    super(id);
  }

  /**
   * Puts it under another framework.
   * @see #getUnderlyingFrameworkTypeId()
   */
  @Nullable
  public FrameworkGroup<?> getParentGroup() {
    return null;
  }

  /**
   * Puts it under framework group.
   * @see #getParentGroup()
   */
  @Nullable
  public String getUnderlyingFrameworkTypeId() {
    return null;
  }

  @NotNull
  public abstract FrameworkSupportInModuleProvider createProvider();

  public <V extends FrameworkVersion> List<V> getVersions() {
    return Collections.emptyList();
  }

  public FrameworkRole[] getRoles() {
    FrameworkGroup<?> parentGroup = getParentGroup();
    if (parentGroup == null) {
      String id = getUnderlyingFrameworkTypeId();
      if (id != null) {
        FrameworkSupportInModuleProvider provider = FrameworkSupportUtil.findProvider(id);
        if (provider != null) return provider.getRoles();
      }
      return FrameworkRole.UNKNOWN;
    }
    else {
      FrameworkRole role = parentGroup.getRole();
      return null == role ? FrameworkRole.UNKNOWN : new FrameworkRole[]{role};
    }
  }
}
