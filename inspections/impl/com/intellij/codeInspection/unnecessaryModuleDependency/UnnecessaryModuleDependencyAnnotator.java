/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(final RefManager manager) {
    myManager = manager;
  }



  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    final PsiElement onElement = refWhat.getElement();
    final PsiElement fromElement = refFrom.getElement();
    if (onElement != null && fromElement!= null){
      final Module onModule = ModuleUtil.findModuleForPsiElement(onElement);
      final Module fromModule = ModuleUtil.findModuleForPsiElement(fromElement);
      if (onModule != null && fromModule != null && onModule != fromModule){
        final RefModule refModule = myManager.getRefModule(fromModule);
        if (refModule != null) {
          Set<Module> modules = refModule.getUserData(DEPENDENCIES);
          if (modules == null){
            modules = new HashSet<Module>();
            refModule.putUserData(DEPENDENCIES, modules);
          }
          modules.add(onModule);
        }
      }
    }
  }
}
