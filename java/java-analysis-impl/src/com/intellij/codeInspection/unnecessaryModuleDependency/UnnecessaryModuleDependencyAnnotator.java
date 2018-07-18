package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private final RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(final RefManager manager) {
    myManager = manager;
  }

  @Override
  public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
    if (what != null && from != null){
      //from should be always in sources
      final Module fromModule = ModuleUtilCore.findModuleForFile(from.getContainingFile());
      final Set<Module> onModules = getAllPossibleWhatModules(what);
      if (onModules != null && fromModule != null){
        final RefModule refModule = myManager.getRefModule(fromModule);
        if (refModule != null) {
          HashSet<Module> modules = new HashSet<>(onModules);
          collectRequiredModulesInHierarchy(what, modules);
          modules.remove(fromModule);
          getModules(refModule).addAll(modules);
        }
      }
    }
  }

  @Override
  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    //case when both from and what are located in the scope, no library dependency expected
    RefModule fromModule = refFrom.getModule();
    RefModule whatModule = refWhat.getModule();
    if (fromModule != null && whatModule != null) {
      Set<Module> currentFromModules = getModules(fromModule);
      currentFromModules.add(whatModule.getModule());
      Set<Module> modules = refWhat.getUserData(DEPENDENCIES);
      if (modules != null) {
        currentFromModules.addAll(modules);
      }
    }
  }

  @Override
  public void onInitialize(RefElement refElement) {
    PsiElement element = refElement.getElement();
    RefModule refModule = refElement.getModule();
    if (refModule != null) {
      HashSet<Module> modules = new HashSet<>();
      collectRequiredModulesInHierarchy(element, modules);
      modules.remove(refModule.getModule());
      if (!modules.isEmpty()) {
        refElement.putUserData(DEPENDENCIES, modules);
        getModules(refModule).addAll(modules);
      }
    }
  }

  private void collectRequiredModulesInHierarchy(PsiElement element, Set<? super Module> modules) {
    if (element instanceof PsiClass) {
      processClassHierarchy((PsiClass)element, modules);
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      Set<PsiClass> classes = new HashSet<>();
      processTypeHierarchy(classes, method.getReturnType(), modules);
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        processTypeHierarchy(classes, parameter.getType(), modules);
      }
    }
    else if (element instanceof PsiField) {
      PsiClass aClass = PsiUtil.resolveClassInType(((PsiField)element).getType());
      if (aClass != null) {
        processClassHierarchy(aClass, modules);
      }
    }
  }

  private void processTypeHierarchy(Set<? super PsiClass> classes, PsiType returnType, Set<? super Module> modules) {
    PsiClass aClass = PsiUtil.resolveClassInType(returnType);
    if (aClass != null && classes.add(aClass)) {
      processClassHierarchy(aClass, modules);
    }
  }

  private void processClassHierarchy(PsiClass currentClass, Set<? super Module> modules) {
    LinkedHashSet<PsiClass> superClasses = new LinkedHashSet<>();
    RefElement refClass = myManager.getReference(currentClass);
    if (!(refClass instanceof RefClass)) {
      InheritanceUtil.getSuperClasses(currentClass, superClasses, false);
    }
    else {
      for (RefClass aClass : ((RefClass)refClass).getBaseClasses()) {
        PsiClass superClass = aClass.getElement();
        if (superClass != null) {
          superClasses.add(superClass);
        }
      }
    }
    for (PsiClass superClass : superClasses) {
      Set<Module> onModules = getAllPossibleWhatModules(superClass);
      if (onModules != null) modules.addAll(onModules);
    }
  }

  /**
   * Returns all owner modules for a library or single module set for a source outside of the inspecting scope
   */
  private static Set<Module> getAllPossibleWhatModules(@NotNull PsiElement what) {
    VirtualFile vFile = PsiUtilCore.getVirtualFile(what);
    if (vFile == null) return null;
    Project project = what.getProject();
    final ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>(orderEntries.size());
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) return null;
        modules.add(orderEntry.getOwnerModule());
      }
      return modules;
    }
    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    return module != null ? Collections.singleton(module) : null;
  }

  private static Set<Module> getModules(RefModule refModule) {
    Set<Module> modules = refModule.getUserData(DEPENDENCIES);
    if (modules == null){
      modules = new HashSet<>();
      refModule.putUserData(DEPENDENCIES, modules);
    }
    return modules;
  }
}
