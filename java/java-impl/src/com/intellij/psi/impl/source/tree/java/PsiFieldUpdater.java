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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 26, 2007
 * Time: 3:52:05 PM
 */
package com.intellij.psi.impl.source.tree.java;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class PsiFieldUpdater<T,V> {
  private static final Unsafe unsafe = getUnsafe();

  private static Unsafe getUnsafe() {
    Unsafe unsafe = null;
    try {
      Class uc = Unsafe.class;
      Field[] fields = uc.getDeclaredFields();
      for (Field field : fields) {
        if (field.getName().equals("theUnsafe")) {
          field.setAccessible(true);
          unsafe = (Unsafe)field.get(uc);
          break;
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return unsafe;
  }

  private final long offset;

  public static <T,V> PsiFieldUpdater<T,V> forOnlyFieldWithType(Class<T> ownerClass, Class<V> fieldType) {
    return new PsiFieldUpdater<T,V>(ownerClass, fieldType);
  }
  private PsiFieldUpdater(Class<T> ownerClass, Class<V> fieldType) {
    Field[] declaredFields = ownerClass.getDeclaredFields();
    Field found = null;
    for (Field field : declaredFields) {
      if (field.getType().equals(fieldType)) {
        if (found == null) {
          found = field;
        }
        else {
          throw new IllegalArgumentException("Two fields with the "+fieldType+" found in the "+ownerClass+": "+found.getName() + " and "+field.getName());
        }
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("No field with the "+fieldType+" found in the "+ownerClass);
    }
    found.setAccessible(true);
    offset = unsafe.objectFieldOffset(found);
  }

  public boolean compareAndSet(T owner, V expected, V newValue) {
    return unsafe.compareAndSwapObject(owner, offset, expected, newValue);
  }
}