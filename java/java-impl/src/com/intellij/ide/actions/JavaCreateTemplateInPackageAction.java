/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.util.Set;

public abstract class JavaCreateTemplateInPackageAction<T extends PsiElement> extends CreateTemplateInPackageAction<T> {

  protected JavaCreateTemplateInPackageAction(String text, String description, Icon icon, boolean inSourceOnly) {
    super(text, description, icon, inSourceOnly ? JavaModuleSourceRootTypes.SOURCES : null);
  }

  protected JavaCreateTemplateInPackageAction(String text,
                                              String description,
                                              Icon icon,
                                              Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    super(text, description, icon, rootTypes);
  }

  @Override
  protected boolean checkPackageExists(PsiDirectory directory) {
    return doCheckPackageExists(directory);
  }

  public static boolean doCheckPackageExists(PsiDirectory directory) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
    if (pkg == null) {
      return false;
    }

    String name = pkg.getQualifiedName();
    return StringUtil.isEmpty(name) || PsiNameHelper.getInstance(directory.getProject()).isQualifiedName(name);
  }
}
