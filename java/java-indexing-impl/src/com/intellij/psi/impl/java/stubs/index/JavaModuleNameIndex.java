/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.compareVersionNumbers;

public class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
  private static final JavaModuleNameIndex ourInstance = new JavaModuleNameIndex();

  public static JavaModuleNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 2 : 0);
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiJavaModule> getKey() {
    return JavaStubIndexKeys.MODULE_NAMES;
  }

  @Override
  public Collection<PsiJavaModule> get(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    Collection<PsiJavaModule> modules = StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope), PsiJavaModule.class);
    if (modules.size() > 1) {
      modules = filterVersions(project, modules);
    }
    return modules;
  }

  private static Collection<PsiJavaModule> filterVersions(Project project, Collection<PsiJavaModule> modules) {
    Map<VirtualFile, PsiJavaModule> filter = ContainerUtil.newHashMap();
    Set<PsiJavaModule> screened = ContainerUtil.newHashSet();

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    for (PsiJavaModule module : modules) {
      VirtualFile file = module.getContainingFile().getVirtualFile();
      if (index.isInLibraryClasses(file)) {
        VirtualFile classRoot = index.getClassRootForFile(file);
        if (classRoot != null) {
          PsiJavaModule previous = filter.get(classRoot);
          if (previous == null) {
            filter.put(classRoot, module);
          }
          else if (compareVersionNumbers(fileVersion(file), fileVersion(previous.getContainingFile().getVirtualFile())) < 0) {
            filter.put(classRoot, module);
            screened.add(previous);
          }
          else {
            screened.add(module);
          }
        }
      }
    }

    return screened.isEmpty() ? modules : modules.stream().filter(module -> !screened.contains(module)).collect(Collectors.toList());
  }

  private static final Pattern MULTI_RESOLVE_VERSION = Pattern.compile("/META-INF/versions/([^/]+)/" + PsiJavaModule.MODULE_INFO_CLS_FILE);

  private static String fileVersion(VirtualFile file) {
    Matcher matcher = MULTI_RESOLVE_VERSION.matcher(file.getPath());
    return matcher.find() ? matcher.group(1) : "0";
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  }
}