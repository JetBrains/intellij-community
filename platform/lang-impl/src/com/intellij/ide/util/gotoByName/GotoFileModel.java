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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoFileItemProvider;
import com.intellij.ide.actions.NonProjectScopeDisablerEP;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Model for "Go to | File" action
 */
public class GotoFileModel extends FilteringGotoByModel<FileType> implements DumbAware {
  private final int myMaxSize;

  public GotoFileModel(@NotNull Project project) {
    super(project, Extensions.getExtensions(ChooseByNameContributor.FILE_EP_NAME));
    myMaxSize = ApplicationManager.getApplication().isUnitTestMode() ? Integer.MAX_VALUE : WindowManagerEx.getInstanceEx().getFrame(project).getSize().width;
  }

  @NotNull
  @Override
  public ChooseByNameItemProvider getItemProvider(@Nullable PsiElement context) {
    for (GotoFileCustomizer customizer : Extensions.getExtensions(GotoFileCustomizer.EP_NAME)) {
      GotoFileItemProvider provider = customizer.createItemProvider(myProject, context, this);
      if (provider != null) return provider;
    }
    return new GotoFileItemProvider(myProject, context, this);
  }

  @Override
  protected boolean acceptItem(final NavigationItem item) {
    if (item instanceof PsiFile) {
      final PsiFile file = (PsiFile)item;
      final Collection<FileType> types = getFilterItems();
      // if language substitutors are used, PsiFile.getFileType() can be different from
      // PsiFile.getVirtualFile().getFileType()
      if (types != null) {
        if (types.contains(file.getFileType())) return true;
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && types.contains(vFile.getFileType())) return true;
        return false;
      }
      return true;
    }
    else {
      return super.acceptItem(item);
    }
  }

  @Nullable
  @Override
  protected FileType filterValueFor(NavigationItem item) {
    return item instanceof PsiFile ? ((PsiFile) item).getFileType() : null;
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotofile.enter.file.name");
  }

  @Override
  public String getCheckBoxName() {
    if (NonProjectScopeDisablerEP.isSearchInNonProjectDisabled()) {
      return null;
    }
    return IdeBundle.message("checkbox.include.non.project.files");
  }

  @Override
  public char getCheckBoxMnemonic() {
    return SystemInfo.isMac?'P':'n';
  }

  @Override
  public String getNotInMessage() {
    return IdeBundle.message("label.no.non.java.files.found");
  }

  @Override
  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.files.found");
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToClass.toSaveIncludeLibraries") &&
           propertiesComponent.isTrueValue("GoToFile.includeJavaFiles");
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if (propertiesComponent.isTrueValue("GoToClass.toSaveIncludeLibraries")) {
      propertiesComponent.setValue("GoToFile.includeJavaFiles", Boolean.toString(state));
    }
  }

  @Override
  public PsiElementListCellRenderer getListCellRenderer() {
    return new GotoFileCellRenderer(myMaxSize);
  }

  @Override
  public boolean sameNamesForProjectAndLibraries() {
    return !FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  }

  @Override
  @Nullable
  public String getFullName(final Object element) {
    if (element instanceof PsiFileSystemItem) {
      VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
      VirtualFile root = getTopLevelRoot(file);
      return root != null ? GotoFileCellRenderer.getRelativePathFromRoot(file, root)
                          : GotoFileCellRenderer.getRelativePath(file, myProject);
    }

    return getElementName(element);
  }

  private VirtualFile getTopLevelRoot(VirtualFile file) {
    return JBIterable.generate(getContentRoot(file), r -> getContentRoot(r.getParent())).last();
  }

  private VirtualFile getContentRoot(@Nullable VirtualFile file) {
    return file == null ? null : ProjectFileIndex.SERVICE.getInstance(myProject).getContentRootForFile(file);
  }

  @Override
  @NotNull
  public String[] getSeparators() {
    return new String[] {"/", "\\"};
  }

  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.class";
  }

  @Override
  public boolean willOpenEditor() {
    return true;
  }

  @NotNull
  @Override
  public String removeModelSpecificMarkup(@NotNull String pattern) {
    if ((pattern.endsWith("/") || pattern.endsWith("\\"))) {
      return pattern.substring(0, pattern.length() - 1);
    }
    return pattern;
  }
}