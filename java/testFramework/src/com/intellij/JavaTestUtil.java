// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public final class JavaTestUtil {

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  public static String getRelativeJavaTestDataPath() {
    final String absolute = getJavaTestDataPath();
    return StringUtil.trimStart(absolute, PathManager.getHomePath());
  }

  @TestOnly
  public static Sdk setupInternalJdkAsTestJDK(@NotNull Disposable parentDisposable, @Nullable String testJdkName) {
    Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    if (testJdkName == null) {
      testJdkName = internalJdk.getName();
    }
    String finalJdkName = testJdkName;
    return WriteAction.compute(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

      Sdk oldJdk = jdkTable.findJdk(finalJdkName);
      if (oldJdk != null) {
        jdkTable.removeJdk(oldJdk);
      }

      Sdk jdk = internalJdk;
      if (!internalJdk.getName().equals(finalJdkName)) {
        try {
          ProjectJdkImpl copy = (ProjectJdkImpl)internalJdk.clone();
          copy.setName(finalJdkName);
          jdk = copy;
        }
        catch (CloneNotSupportedException e) {
          throw new RuntimeException(e);
        }
      }
      jdkTable.addJdk(jdk, parentDisposable);
      return jdk;
    });
  }

  public static LanguageLevel getMaxRegisteredLanguageLevel() {
    LanguageLevel[] values = LanguageLevel.values();
    return values[values.length - 1];
  }
}