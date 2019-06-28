/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An extension that enumerates classes defining stub element types for all languages. Index infrastructure needs
 * their names to work correctly, so they're loaded via this extension when necessary.<p></p>
 *
 * To speed up IDE loading, it's recommended that this extension is used for interfaces containing only
 * {@link IStubElementType} (or {@link ObjectStubSerializer}) constants,
 * and all other language's element types are kept in a separate interface that can be loaded later.
 *
 * @author yole
 */
public class StubElementTypeHolderEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubElementTypeHolderEP");

  public static final ExtensionPointName<StubElementTypeHolderEP> EP_NAME = ExtensionPointName.create("com.intellij.stubElementTypeHolder");

  @Attribute("class")
  public String holderClass;

  /**
   * Allows to avoid class initialization by declaring that the stub element type holder obeys the following contract:
   * <ul>
   * <li>It's an interface</li>
   * <li>All stub element types to load are declared as fields in the interface itself, not in super-interfaces</li>
   * <li>For all {@link IStubElementType} fields, their {@link IStubElementType#getExternalId()}:
   * <ul>
   *   <li>doesn't depend on class fields, so that it can be called during IStubElementType construction</li>
   *   <li>effectively returns {@code "somePrefix" + debugName}</li>
   *   <li>{@code debugName} is equal to the field name</li>
   *   <li>"somePrefix" is the value of "externalIdPrefix" attribute</li>
   * </ul>
   * </li>
   * <li>For all other fields, if any, the same {@code prefix+debugName} concatenation doesn't produce an external id used by any other stub element type</li>
   * </ul>
   */
  @Attribute("externalIdPrefix")
  @Nullable
  public String externalIdPrefix;

  List<StubFieldAccessor> initializeOptimized() {
    try {
      if (externalIdPrefix != null) {
        List<StubFieldAccessor> result = new ArrayList<>();
        Class<?> aClass = Class.forName(holderClass, false, getLoaderForClass());
        assert aClass.isInterface();
        for (Field field : aClass.getDeclaredFields()) {
          result.add(new StubFieldAccessor(externalIdPrefix + field.getName(), field));
        }
        return result;
      } else {
        findClass(holderClass);
      }
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  /**
   * @deprecated please don't use this extension to ensure something is initialized as a side effect of stub element type loading,
   * create your own narrow-scoped extension instead
   */
  @Deprecated
  public void initialize() {
    try {
      findClass(holderClass);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  @Override
  public String toString() {
    return holderClass;
  }
}
