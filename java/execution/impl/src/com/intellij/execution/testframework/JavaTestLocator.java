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
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaTestLocator implements SMTestLocator {
  public static final String SUITE_PROTOCOL = "java:suite";
  public static final String TEST_PROTOCOL = "java:test";

  public static final JavaTestLocator INSTANCE = new JavaTestLocator();

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    List<Location> results = Collections.emptyList();

    String paramName = null;
    int idx = path.indexOf('[');
    if (idx >= 0) {
      paramName = path.substring(idx);
      path = path.substring(0, idx);
    }

    if (SUITE_PROTOCOL.equals(protocol)) {
      path = StringUtil.trimEnd(path, ".");
      PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), path, null, true, scope);
      if (aClass != null) {
        results = ContainerUtil.newSmartList();
        results.add(paramName != null ? PsiMemberParameterizedLocation.getParameterizedLocation(aClass, paramName)
                                      : new PsiLocation<>(project, aClass));
      }
    }
    else if (TEST_PROTOCOL.equals(protocol)) {
      String className = StringUtil.getPackageName(path);
      if (!StringUtil.isEmpty(className)) {
        String methodName = StringUtil.getShortName(path);
        PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className, null, true, scope);
        if (aClass != null) {
          results = ContainerUtil.newSmartList();
          PsiMethod[] methods = aClass.findMethodsByName(methodName.trim(), true);
          if (methods.length > 0) {
            for (PsiMethod method : methods) {
              results.add(paramName != null ? new PsiMemberParameterizedLocation(project, method, aClass, paramName)
                                            : MethodLocation.elementInClass(method, aClass));
            }
          }
        }
      }
    }

    return results;
  }
}
