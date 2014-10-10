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
package com.intellij.psi;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class WeigherExtensionPoint extends AbstractExtensionPointBean implements KeyedLazyInstance<Weigher> {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("id")
  public String id;

  private final NotNullLazyValue<Weigher> myHandler = new NotNullLazyValue<Weigher>() {
    @Override
    @NotNull
    protected final Weigher compute() {
      try {
        Class<Weigher> tClass = findClass(implementationClass);
        final Weigher weigher = ReflectionUtil.newInstance(tClass);
        weigher.setDebugName(id);
        return weigher;
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @NotNull
  @Override
  public Weigher getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return key;
  }
}
