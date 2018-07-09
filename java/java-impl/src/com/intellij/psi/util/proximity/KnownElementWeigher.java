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
package com.intellij.psi.util.proximity;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkUtils;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;

/**
 * @author peter
*/
public class KnownElementWeigher extends ProximityWeigher {
  private static final Set<String> POPULAR_JDK_CLASSES = ContainerUtil.newHashSet(
    JAVA_LANG_STRING,
    JAVA_LANG_CLASS,
    System.class.getName(), JAVA_LANG_RUNNABLE,
    JAVA_LANG_EXCEPTION, JAVA_LANG_THROWABLE, JAVA_LANG_RUNTIME_EXCEPTION,
    JAVA_UTIL_ARRAY_LIST, JAVA_UTIL_HASH_MAP, JAVA_UTIL_HASH_SET
  );

  @Override
  public Comparable weigh(@NotNull final PsiElement element, @NotNull final ProximityLocation location) {
    Project project = location.getProject();
    if (project == null) return 0;

    Comparable tests = getTestFrameworkWeight(element, location, project);
    if (tests != null) return tests;

    if (element instanceof PsiMember && JavaCompletionUtil.isInExcludedPackage((PsiMember)element, false)) {
      return -1;
    }

    if (JdkUtils.getJdkForElement(element) == null) {
      return 0;
    }

    if (element instanceof PsiClass) {
      return getJdkClassProximity((PsiClass)element);
    }
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        String methodName = method.getName();
        if ("finalize".equals(methodName) || "registerNatives".equals(methodName) || methodName.startsWith("wait") || methodName.startsWith("notify")) {
          if (JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
            return -1;
          }
        }
        if (isGetClass(method)) {
          return -1;
        }
        if ("subSequence".equals(methodName)) {
          if (JAVA_LANG_STRING.equals(containingClass.getQualifiedName())) {
            return -1;
          }
        }
        if (JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          return 0;
        }
        return getJdkClassProximity(method.getContainingClass());
      }
    }
    if (element instanceof PsiField) {
      return getJdkClassProximity(((PsiField)element).getContainingClass());
    }
    return 0;
  }

  @Nullable
  private static Integer getTestFrameworkWeight(@NotNull PsiElement element, @NotNull ProximityLocation location, @NotNull Project project) {
    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)element).getQualifiedName();
      if (qualifiedName != null) {
        if (qualifiedName.startsWith("org.testng.internal")) {
          return -1;
        }
        VirtualFile locationFile = PsiUtilCore.getVirtualFile(location.getPosition());
        if (locationFile != null &&
            ProjectFileIndex.SERVICE.getInstance(project).isInTestSourceContent(locationFile) &&
            (qualifiedName.contains("junit") || qualifiedName.contains("test"))) {
          return 1;
        }
      }
    }
    return null;
  }

  public static boolean isGetClass(PsiMethod method) {
    return "getClass".equals(method.getName()) && method.getParameterList().getParametersCount() <= 0;
  }

  private static Comparable getJdkClassProximity(@Nullable PsiClass element) {
    String qname = element == null ? null : element.getQualifiedName();
    if (qname == null) return null;
    
    if (isDispreferredName(qname)) return -1;

    if (element.getContainingClass() != null) return 0;
    
    String pkg = StringUtil.getPackageName(qname);
    if (qname.equals(JAVA_LANG_OBJECT)) return 5;
    if (POPULAR_JDK_CLASSES.contains(qname)) return 8;
    if (pkg.equals("java.lang")) return 6;
    if (pkg.equals("java.util")) return 7;

    if (qname.startsWith("java.lang")) return 5;
    if (qname.startsWith("java.util")) return 4;

    if (pkg.equals("javax.swing")) return 3;
    if (qname.startsWith("java.")) return 2;
    if (qname.startsWith("javax.")) return 1;
    return 0;
  }

  private static boolean isDispreferredName(String qname) {
    return qname.startsWith("com.") || qname.startsWith("net.");
  }
}