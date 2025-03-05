// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.SmartList;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Protocol format as follows:
 * <p>
 * java:suite://className
 * java:test://className/methodName
 * <p>
 * "/" can't appear as part of package name and thus can be used as a valid separator between fully qualified class name and method name
 */
public class JavaTestLocator implements SMTestLocator, DumbAware {

  private static final Logger LOG = Logger.getInstance(JavaTestLocator.class);

  public static final String SUITE_PROTOCOL = "java:suite";
  public static final String TEST_PROTOCOL = "java:test";

  public static final JavaTestLocator INSTANCE = new JavaTestLocator();

  @Override
  public @NotNull List<Location> getLocation(@NotNull String protocol,
                                             @NotNull String sourcePath,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope) {
    DumbService dumbService = DumbService.getInstance(project);
    try {
      return dumbService.computeWithAlternativeResolveEnabled((ThrowableComputable<List<Location>, Throwable>)() -> {
        String path = sourcePath;
        String paramName = null;
        int idx = path.indexOf('[');
        if (idx >= 0) {
          paramName = path.substring(idx);
          path = path.substring(0, idx);
        }

        List<Location> results = Collections.emptyList();
        if (SUITE_PROTOCOL.equals(protocol)) {
          path = StringUtil.trimEnd(path, ".");
          PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), path, null, true, scope);
          if (aClass != null) {
            results = new SmartList<>();
            results.add(createClassNavigatable(paramName, aClass));
          }
          else {
            results = collectMethodNavigatables(path, project, scope, paramName);
          }
        }
        else if (TEST_PROTOCOL.equals(protocol)) {
          results = collectMethodNavigatables(path, project, scope, paramName);
        }

        return results;
      });
    }
    catch (IndexNotReadyException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull List<Location> getLocation(@NotNull String protocol,
                                             @NotNull String path,
                                             @Nullable String metainfo,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope) {
    List<Location> locations = getLocation(protocol, path, project, scope);
    if (metainfo != null) {
      for (Location location : locations) {
        PsiElement element = location.getPsiElement();
        if (element instanceof PsiMethod) {
          if (StringUtil.equalsIgnoreWhitespaces(metainfo, ClassUtil.getVMParametersMethodSignature((PsiMethod)element))) {
            return Collections.singletonList(location);
          }
        }
        else if (element instanceof PsiClass) {
          String[] lineColumn = metainfo.split(":");
          if (lineColumn.length == 2) {
            try {
              int line = Integer.parseInt(lineColumn[0]);
              int col = Integer.parseInt(lineColumn[1]);
              return Collections.singletonList(new PsiLocation<>(project, element) {
                @Override
                public OpenFileDescriptor getOpenFileDescriptor() {
                  VirtualFile file = getVirtualFile();
                  return file != null ? new OpenFileDescriptor(project, file, line, col) : null;
                }
              });
            }
            catch (NumberFormatException ignored) { }
          }
        }
      }
    }
    return locations;
  }

  @Override
  public @NotNull List<Location> getLocation(@NotNull String stacktraceLine, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    StackTraceLine line = new StackTraceLine(project, stacktraceLine);
    return getLocation(TEST_PROTOCOL, line.getClassName() + "/" + line.getMethodName(), project, scope);
  }

  private static List<Location> collectMethodNavigatables(@NotNull String path,
                                                          @NotNull Project project,
                                                          @NotNull GlobalSearchScope scope,
                                                          String paramName) {
    int classSeparatorIdx = path.indexOf('/');
    if (classSeparatorIdx > 0) {
      String className = path.substring(0, classSeparatorIdx);
      String methodName = path.substring(classSeparatorIdx + 1);
      return collectMethodNavigatables(project, scope, paramName, methodName, className);
    }
    else {
      //fallback to the old protocol format : java:test://className.methodName
      String className = StringUtil.getPackageName(path);
      String methodName = StringUtil.getShortName(path);
      if (!className.isEmpty()) {
        return collectMethodNavigatables(project, scope, paramName, methodName, className);
      }
    }
    return Collections.emptyList();
  }

  private static List<Location> collectMethodNavigatables(@NotNull Project project,
                                                          @NotNull GlobalSearchScope scope,
                                                          String paramName,
                                                          String methodName,
                                                          String className) {
    List<Location> results = Collections.emptyList();
    PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className, null, true, scope);
    if (aClass != null) {
      results = new SmartList<>();
      if (methodName.trim().equals(aClass.getName())) {
        results.add(createClassNavigatable(paramName, aClass));
      }
      else {
        PsiMethod[] methods = aClass.findMethodsByName(methodName.trim(), true);
        for (PsiMethod method : methods) {
          results.add(paramName != null ? new PsiMemberParameterizedLocation(project, method, aClass, paramName)
                                        : MethodLocation.elementInClass(method, aClass));
        }
      }
    }
    return results;
  }

  private static Location createClassNavigatable(String paramName, @NotNull PsiClass aClass) {
    return paramName != null ? PsiMemberParameterizedLocation.getParameterizedLocation(aClass, paramName)
                             : new PsiLocation<>(aClass.getProject(), aClass);
  }

  public static @NotNull String createLocationUrl(@NotNull String protocol, @NotNull String className) {
    return createLocationUrl(protocol, className, null);
  }

  public static @NotNull String createLocationUrl(@NotNull String protocol, @NotNull String className, @Nullable String methodName) {
    return createLocationUrl(protocol, className, methodName, null);
  }

  public static @NotNull String createLocationUrl(
    @NotNull String protocol,
    @NotNull String className,
    @Nullable String methodName,
    @Nullable String paramName
  ) {
    var baseUrl = protocol + URLUtil.SCHEME_SEPARATOR;
    if (methodName == null) {
      return baseUrl + className;
    }
    methodName = StringUtil.trimEnd(methodName, "()");
    if (paramName == null) {
      return baseUrl + className + "/" + methodName;
    }
    return baseUrl + className + "/" + methodName + paramName;
  }
}
