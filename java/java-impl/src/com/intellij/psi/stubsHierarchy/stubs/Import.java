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
package com.intellij.psi.stubsHierarchy.stubs;

import com.intellij.psi.stubsHierarchy.impl.QualifiedName;
import com.intellij.psi.stubsHierarchy.impl.NameEnvironment;
import com.intellij.util.ArrayUtil;

public class Import {
  public final static long[] EMPTY_ARRAY = ArrayUtil.EMPTY_LONG_ARRAY;

  public static int onDemandMask = 1 << 29;
  public static int staticMask = 1 << 30;

  public static int mask = ~(onDemandMask | staticMask);

  public static int getAlias(long importMask) {
    return (int)(importMask >>> 32);
  }

  public static QualifiedName getFullName(long importMask, NameEnvironment nameEnvironment) {
    return nameEnvironment.qualifiedName(getFullNameId(importMask));
  }

  private static int getFullNameId(long importMask) {
    int fullNameId = (int)importMask;
    fullNameId &= mask;
    return fullNameId;
  }

  public static boolean isOnDemand(long importMask) {
    return (importMask & onDemandMask) != 0;
  }

  public static boolean isStatic(long importMask) {
    return (importMask & staticMask) != 0;
  }

  public static long mkImport(QualifiedName fullname, boolean importStatic, boolean onDemand, int alias) {
    long lower = fullname.myId;
    if (importStatic) lower |= staticMask;
    if (onDemand) lower |= onDemandMask;
    return (((long)alias) << 32) | lower;
  }
}
