/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Allows plugins to handle the Move refactoring for a file in a custom way.
 *
 * @author Maxim.Mossienko
 */
public abstract class MoveFileHandler {
  private static final ExtensionPointName<MoveFileHandler> EP_NAME = ExtensionPointName.create("com.intellij.moveFileHandler");

  /**
   * Checks whether a file can be handled by this move handler.
   *
   * @param element the file being moved.
   * @return true if this handler can handle this file, false otherwise.
   */
  public abstract boolean canProcessElement(PsiFile element);

  /**
   * Performs any necessary modifications of the file contents before the move.
   *
   * @param file the file being moved.
   * @param moveDestination the directory to which the file is being moved.
   * @param oldToNewMap the map of elements which can be referenced from other files directly (not through a file reference)
   *                    to their counterparts after the move. The handler needs to add elements to this map according to the
   *                    file modifications that it has performed.
   */
  public abstract void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap);

  /**
   * Finds the list of references to the file being moved that will need to be updated during the move refactoring.
   *
   * @param psiFile the file being moved.
   * @param newParent the directory to which the file is being moved.
   * @param searchInComments if true, search for references in comments has been requested.
   * @param searchInNonJavaFiles if true, search for references in non-code files (such as .xml) has been requested.
   * @return the list of usages that need to be updated, or null if nothing needs to be updated.
   */
  @Nullable
  public abstract List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles);

  /**
   * After a file has been moved, updates the references to the file  so that they point to the new location of the file.
   *
   * @param usageInfos  the list of references, as returned from {@link #findUsages}
   * @param oldToNewMap the map of all moved elements, filled by {@link #prepareMovedFile}
   */
  public abstract void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) ;

  /**
   * Updates the contents of the file after it has been moved (e.g. updates the package statement to correspond to the
   * new location of a Java class).
   *
   * @param file the moved file.
   */
  public abstract void updateMovedFile(PsiFile file) throws IncorrectOperationException;

  @NotNull
  public static MoveFileHandler forElement(PsiFile element) {
    for(MoveFileHandler processor: Extensions.getExtensions(EP_NAME)) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return DEFAULT;
  }

  private static final MoveFileHandler DEFAULT = new MoveFileHandler() {
    @Override
    public boolean canProcessElement(final PsiFile element) {
      return true;
    }

    @Override
    public void prepareMovedFile(final PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {

    }

    @Override
    public void updateMovedFile(final PsiFile file) throws IncorrectOperationException {

    }

    @Override
    public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
      return null;
    }

    @Override
    public void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) {

    }
  };



}
