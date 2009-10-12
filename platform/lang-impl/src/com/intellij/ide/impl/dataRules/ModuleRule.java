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

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 10, 2004
 */
public class ModuleRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    Object moduleContext = dataProvider.getData(DataConstants.MODULE_CONTEXT);
    if (moduleContext != null) {
      return moduleContext;
    }
    Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
    if (project == null) {
      PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
      if (element == null || !element.isValid()) return null;
      project = element.getProject();
    }

    VirtualFile virtualFile = (VirtualFile)dataProvider.getData(DataConstants.VIRTUAL_FILE);
    if (virtualFile == null) {
      GetDataRule dataRule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(DataConstants.VIRTUAL_FILE);
      if (dataRule != null) {
        virtualFile = (VirtualFile)dataRule.getData(dataProvider);
      }
    }

    if (virtualFile == null) {
      return null;
    }

    return ModuleUtil.findModuleForFile(virtualFile, project);
  }
}
