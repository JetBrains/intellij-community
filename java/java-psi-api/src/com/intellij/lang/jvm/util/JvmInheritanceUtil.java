// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.jvm.util.JvmHierarchyUtil.testSupers;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public class JvmInheritanceUtil {

  public static boolean isInheritor(@NotNull JvmClass potentialInheritor, @NotNull String baseFqn) {
    if (JAVA_LANG_OBJECT.equals(baseFqn)) return true;
    return testSupers(potentialInheritor, false, superClass -> baseFqn.equals(superClass.getQualifiedName()));
  }
}
