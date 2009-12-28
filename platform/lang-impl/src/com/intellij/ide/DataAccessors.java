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

package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * @author yole
 */
public class DataAccessors {
  @Deprecated
  public static final DataAccessor<Project> PROJECT = new DataAccessor.SimpleDataAccessor<Project>(PlatformDataKeys.PROJECT.getName());
  @Deprecated
  public static final DataAccessor<Module> MODULE = new DataAccessor.SimpleDataAccessor<Module>(LangDataKeys.MODULE.getName());
  public static final DataAccessor<PsiManager> PSI_MANAGER = new DataAccessor<PsiManager>() {
    public PsiManager getImpl(DataContext dataContext) throws NoDataException {
      return PsiManager.getInstance(PROJECT.getNotNull(dataContext));
    }
  };
  public static final DataAccessor<PsiFile> PSI_FILE = new DataAccessor<PsiFile>() {
    public PsiFile getImpl(DataContext dataContext) throws NoDataException {
      return PSI_MANAGER.getNotNull(dataContext).findFile(VIRTUAL_FILE.getNotNull(dataContext));
    }
  };
  public static final DataAccessor<VirtualFile> VIRTUAL_FILE = new DataAccessor.SimpleDataAccessor<VirtualFile>(PlatformDataKeys.VIRTUAL_FILE.getName());
  public static final DataAccessor<VirtualFile> VIRTUAL_DIR_OR_PARENT = new DataAccessor<VirtualFile>() {
    public VirtualFile getImpl(DataContext dataContext) throws NoDataException {
      VirtualFile virtualFile = VIRTUAL_FILE.getNotNull(dataContext);
      return virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
    }
  };
  public static final DataAccessor<VirtualFile> PROJECT_BASE_DIR = new DataAccessor<VirtualFile>() {
    public VirtualFile getImpl(DataContext dataContext) throws NoDataException {
      Project project = PROJECT.getNotNull(dataContext);
      return project.getBaseDir();
    }
  };
  public static final DataAccessor<String> MODULE_FILE_PATH = new DataAccessor<String>() {
    public String getImpl(DataContext dataContext) throws NoDataException {
      Module module = MODULE.getNotNull(dataContext);
      return module.getModuleFilePath();
    }
  };

  private DataAccessors() {
  }
}