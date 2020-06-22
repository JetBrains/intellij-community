// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An extension that enumerates classes defining stub element types for all languages. Index infrastructure needs
 * their names to work correctly, so they're loaded via this extension when necessary.<p></p>
 * <p>
 * To speed up IDE loading, it's recommended that this extension is used for interfaces containing only
 * {@link IStubElementType} (or {@link ObjectStubSerializer}) constants,
 * and all other language's element types are kept in a separate interface that can be loaded later.
 * <p>
 * Also consider specifying {@link #externalIdPrefix} for further speedup.
 *
 * @author yole
 */
public final class StubElementTypeHolderEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(StubElementTypeHolderEP.class);

  public static final ExtensionPointName<StubElementTypeHolderEP> EP_NAME = new ExtensionPointName<>("com.intellij.stubElementTypeHolder");

  @Attribute("class")
  @RequiredElement
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
      }
      else {
        findExtensionClass(holderClass);
      }
    }
    catch (ClassNotFoundException e) {
      LOG.error(new PluginException(e, getPluginId()));
    }
    return Collections.emptyList();
  }

  /**
   * @deprecated please don't use this extension to ensure something is initialized as a side effect of stub element type loading,
   * create your own narrow-scoped extension instead
   */
  @Deprecated
  public void initialize() {
    findClassNoExceptions(holderClass);
  }

  @Override
  public String toString() {
    return holderClass;
  }
}
