/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.javaee.module;

import com.intellij.openapi.module.Module;
import com.sun.org.apache.xml.internal.utils.XMLChar;
import org.jetbrains.annotations.Nullable;

import java.io.File;


/**
 * @author Alexey Kudravtsev
 */
public abstract class ModuleLink extends ContainerElement {

  public ModuleLink(Module parentModule) {
    super(parentModule);
  }

  public abstract @Nullable Module getModule();

  public abstract String getId();

  public abstract boolean hasId(String id);

  public abstract String getName();

  public static String getId(Module module) {
    String baseName = module == null ? "" : new File(module.getModuleFilePath()).getName();
    if (baseName.endsWith(".iml")) baseName = baseName.substring(0, baseName.length() - ".iml".length());
    return convertNameToXmlName(baseName);
  }

  private static String convertNameToXmlName(final String baseName) {
    final StringBuilder builder = new StringBuilder(baseName.length());
    for (int i = 0; i < baseName.length(); i++) {
      char c = baseName.charAt(i);
      if (i == 0 && !XMLChar.isNameStart(c) || !XMLChar.isName(c)) {
        c = '_';
      }
      builder.append(c);
    }
    return builder.toString();
  }

  public static boolean hasId(final Module module, final String id) {
    final String id1 = getId(module);
    return id1.equals(id) || (id1 + ".iml").equals(id);
  }
}
