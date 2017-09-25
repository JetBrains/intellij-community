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
package com.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public class JavaTestUtil {
  private static final String TEST_JDK_NAME = "JDK";

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  public static String getRelativeJavaTestDataPath() {
    final String absolute = getJavaTestDataPath();
    return StringUtil.trimStart(absolute, PathManager.getHomePath());
  }

  @TestOnly
  public static void setupTestJDK(@NotNull Disposable parentDisposable) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

      Sdk jdk = jdkTable.findJdk(TEST_JDK_NAME);
      if (jdk != null) {
        jdkTable.removeJdk(jdk);
      }

      jdkTable.addJdk(getTestJdk(), parentDisposable);
    });
  }

  public static Sdk getTestJdk() {
    try {
      ProjectJdkImpl jdk = (ProjectJdkImpl)JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().clone();
      jdk.setName(TEST_JDK_NAME);
      return jdk;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}