/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.cvsIntegration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.IOException;

public abstract class CvsServices {
  public static CvsServices getInstance(){
    return ApplicationManager.getApplication().getComponent(CvsServices.class);
  }

  public abstract CvsModule[] chooseModules(Project project, boolean allowRootSelection,
                                            boolean allowMultipleSelection,
                                            boolean allowFilesSelection, String title, String selectModulePageTitle);

  public abstract CvsRepository[] getConfiguredRepositories();
  public abstract void showDifferencesForFiles(CvsModule first, CvsModule second, Project project) throws Exception;
  public abstract String getScrambledPasswordForPServerCvsRoot(String cvsRoot);
  public abstract boolean saveRepository(CvsRepository repository);
  public abstract void openInEditor(Project project, CvsModule cvsFile);
  public abstract byte[] getFileContent(Project project, CvsModule cvsFile) throws IOException;
  public abstract CvsResult checkout(String[] modules, File checkoutTo, String directory, boolean makeNewFilesReadOnly, boolean pruneEmptyDirectories, Object keywordSubstitution, Project project, CvsRepository repository);
}
