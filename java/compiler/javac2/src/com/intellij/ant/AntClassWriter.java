/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ant;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;

/**
 * @author yole
 */
public class AntClassWriter extends ClassWriter {
  private final PseudoClassLoader myPseudoClassLoader;

  public AntClassWriter(int flags, final PseudoClassLoader pseudoLoader) {
    super(flags);
    myPseudoClassLoader = pseudoLoader;
  }

  public AntClassWriter(ClassReader classReader, int flags, final PseudoClassLoader pseudoLoader) {
    super(classReader, flags);
    myPseudoClassLoader = pseudoLoader;
  }

  protected String getCommonSuperClass(final String type1, final String type2) {
    try {
      PseudoClassLoader.PseudoClass p1 = myPseudoClassLoader.loadClass(type1);
      PseudoClassLoader.PseudoClass p2 = myPseudoClassLoader.loadClass(type2);

      return p1.getCommonSuperClassName(p2);
    } catch (ClassNotFoundException e) {
        throw new RuntimeException(e.getMessage());
    }
    catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }
}
