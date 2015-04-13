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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaTestLocationProvider implements TestLocationProvider {
  public static final String SUITE_PROTOCOL = "java:suite";
  public static final String TEST_PROTOCOL = "java:test";

  private final GlobalSearchScope myScope;

  public JavaTestLocationProvider(@NotNull GlobalSearchScope scope) {
    myScope = scope;
  }

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocolId, @NotNull String locationData, Project project) {
    List<Location> results = Collections.emptyList();

    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    if (SUITE_PROTOCOL.equals(protocolId)) {
      PsiClass[] classes = javaPsiFacade.findClasses(locationData, myScope);
      if (classes.length > 0) {
        results = ContainerUtil.newSmartList();
        for (PsiClass aClass : classes) {
          results.add(new PsiLocation<PsiClass>(project, aClass));
        }
      }
      else {
        //parameter root for parameterized tests: ClassName.[paramName]
        final String className = StringUtil.getPackageName(locationData);
        classes = javaPsiFacade.findClasses(className, myScope);
        if (classes.length > 0) {
          final String paramName = StringUtil.getShortName(locationData);
          results = ContainerUtil.newSmartList();
          for (PsiClass aClass : classes) {
            results.add(PsiMemberParameterizedLocation.getParameterizedLocation(aClass, paramName));
          }
        }
      }
    }
    else if (TEST_PROTOCOL.equals(protocolId)) {
      final String className = StringUtil.getPackageName(locationData);
      if (!StringUtil.isEmpty(className)) {
        String methodName = StringUtil.getShortName(locationData);
        PsiClass[] classes = javaPsiFacade.findClasses(className, myScope);
        if (classes.length > 0) {
          results = ContainerUtil.newSmartList();
          for (PsiClass aClass : classes) {
            PsiMethod[] methods = aClass.findMethodsByName(methodName, true);
            if (methods.length > 0) {
              for (PsiMethod method : methods) {
                results.add(MethodLocation.elementInClass(method, aClass));
              }
            }
            else {
              //parameterized tests: ClassName.testName[paramName]
              final int paramIdx = methodName.indexOf("[");
              if (paramIdx > -1 && methodName.endsWith("]")) {
                final String paramName = methodName.substring(paramIdx);
                methods = aClass.findMethodsByName(methodName.substring(0, paramIdx), true);
                for (PsiMethod method : methods) {
                  results.add(new PsiMemberParameterizedLocation(project, method, aClass, paramName));
                }
              }
            }
          }
        }
      }
    }

    return results;
  }
}
