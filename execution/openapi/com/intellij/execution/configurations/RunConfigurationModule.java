/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class RunConfigurationModule implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.RunConfigurationModule");
  @NonNls private static final String ELEMENT = "module";
  @NonNls private static final String ATTRIBUTE = "name";
  private Module myModule = null;
  private String myModuleName;
  private final Project myProject;
  private final boolean myClassesInLibraries;

  public RunConfigurationModule(final Project project, final boolean classesInLibs) {
    myProject = project;
    myClassesInLibraries = classesInLibs;
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(final Element element) throws InvalidDataException {
    final List<Element> modules = (List<Element>)element.getChildren(ELEMENT);
    LOG.assertTrue(modules.size() <= 1);
    if (modules.size() == 1) {
      final Element module = modules.get(0);
      final String moduleName = module.getAttributeValue(ATTRIBUTE);  //we are unable to set 'null' module from 'not null' one
      if (moduleName != null && moduleName.length() > 0){
        myModuleName = moduleName;
      }
    }
  }

  public void writeExternal(final Element parent) throws WriteExternalException {
    final Element element = new Element(ELEMENT);
    element.setAttribute(ATTRIBUTE, getModuleName());
    parent.addContent(element);
  }

  public void init() {
    if (getModuleName().trim().length() > 0) return;
    final Module[] modules = getModuleManager().getModules();
    if (modules.length > 0){
      setModule(modules[0]);
    }
  }

  public Project getProject() { return myProject; }

  @Nullable
  public Module getModule() {
    if (myModuleName != null) { //caching
      myModule = findModule(myModuleName);
      myModuleName = null;
    }
    return myModule;
  }

  @Nullable
  public Module findModule(final String moduleName) {
    return getModuleManager().findModuleByName(moduleName);
  }

  public void setModule(final Module module) {
    myModule = module;
  }

  public String getModuleName() {
    final Module module = getModule();
    return module != null ? module.getName() : "";
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  @Nullable
  public PsiClass findClass(final String qualifiedName) {
    if (qualifiedName == null) return null;
    final Module module = getModule();
    final GlobalSearchScope scope;
    if (module != null) {
      scope = myClassesInLibraries ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
              : GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    else {
      scope = myClassesInLibraries ? GlobalSearchScope.allScope(myProject) : GlobalSearchScope.projectScope(myProject);
    }
    return PsiManager.getInstance(myProject).findClass(qualifiedName.replace('$', '.'), scope);
  }

  public static Collection<Module> getModulesForClass(@NotNull final Project project, final String className) {
    if (project.isDefault()) return Arrays.asList(ModuleManager.getInstance(project).getModules());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final PsiClass[] possibleClasses = PsiManager.getInstance(project).findClasses(className, GlobalSearchScope.projectScope(project));

    final Set<Module> modules = new THashSet<Module>();
    for (PsiClass aClass : possibleClasses) {
      Module module = ModuleUtil.findModuleForPsiElement(aClass);
      if (module != null) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }
    else {
      return ModuleUtil.collectModulesDependsOn(modules);
    }
  }

  public void checkForWarning() throws RuntimeConfigurationException {
    final Module module = getModule();
    if (module != null) {
      if (ModuleRootManager.getInstance(module).getJdk() == null) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("no.jdk.specified.for.module.warning.text", module.getName()));
      }
      if (module.isDisposed()){
        throw new RuntimeConfigurationError(ExecutionBundle.message("module.doesn.t.exist.in.project.error.text", module.getName()));
      }
    }
    else {
      throw new RuntimeConfigurationError(ExecutionBundle.message("module.not.specified.error.text"));
    }
  }

  public PsiClass checkClassName(final String className, final String errorMessage) throws RuntimeConfigurationException {
    if (className == null || className.length() == 0) {
      throw new RuntimeConfigurationError(errorMessage);
    }
    return findNotNullClass(className);
  }

  public PsiClass findNotNullClass(final String className) throws RuntimeConfigurationWarning {
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) {
      throw new RuntimeConfigurationWarning(
        ExecutionBundle.message("class.not.found.in.module.error.message", className, getModuleName()));
    }
    return psiClass;
  }

  public PsiClass checkModuleAndClassName(final String className, final String expectedClassMessage) throws RuntimeConfigurationException {
    checkForWarning();
    return checkClassName(className, expectedClassMessage);
  }

}
