/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.jsp.JspFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A PSI element representing a file.
 */
public interface PsiFile extends PsiElement, PsiFileSystemItem {
  /**
   * The empty array of PSI files which can be reused to avoid unnecessary allocations.
   */
  PsiFile[] EMPTY_ARRAY = new PsiFile[0];

  /**
   * Returns the virtual file corresponding to the PSI file.
   *
   * @return the virtual file, or null if the file exists only in memory.
   */
  @Nullable VirtualFile getVirtualFile();

  /**
   * Returns the directory containing the file.
   *
   * @return the containing directory, or null if the file exists only in memory.
   */
  @Nullable PsiDirectory getContainingDirectory();

  /**
   * Gets the modification stamp value. Modification stamp is a value changed by any modification
   * of the content of the file. Note that it is not related to the file modification time.
   *
   * @return the modification stamp value
   * @see com.intellij.openapi.vfs.VirtualFile#getModificationStamp()
   */
  long getModificationStamp();

  /**
   * For Java/JSP files only: returns the list of classes or packages which have been
   * imported on demand (for example, javax.swing.*)
   *
   * @param includeImplicit if true, implicitly imported packages (like java.lang) are included.
   * @param checkIncludes   deprecated, no longer used
   * @return the list of PsiClass or PsiPackage elements for the imports.
   */
  @NotNull PsiElement[] getOnDemandImports(boolean includeImplicit, @Deprecated boolean checkIncludes);

  /**
   * For Java/JSP files only: returns the list of classs which have been imported as
   * single-class imports.
   *
   * @param checkIncludes deprecated, no longer used.
   * @return the list of PsiClass elements for the import.
   */
  @NotNull PsiClass[] getSingleClassImports(@Deprecated boolean checkIncludes);

  /**
   * For Java/JSP files only: returns the list of names of implicitly imported packages
   * (for example, java.lang).
   *
   * @return the list of implicitly imported package names.
   */
  @NotNull String[] getImplicitlyImportedPackages();

  /**
   * For Java/JSP files only: returns the list of reference elements for the
   * implicitly imported packages (for example, java.lang).
   *
   * @return the list of implicitly imported package reference elements.
   */
  @NotNull PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences();

  /**
   * For Java/JSP files only: returns the single-class import statement which references
   * the specified class, or null if there is no such statement.
   *
   * @param aClass the class to return the import statement for.
   * @return the Java code reference under the import statement, or null if there is no such statement.
   */
  @Nullable PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass);

  /**
   * If the file is a non-physical copy of a file, returns the original file which had
   * been copied. Otherwise, returns null.
   *
   * @return the original file of a copy, or null if the file is not a copy.
   */
  @Nullable PsiFile getOriginalFile();

  /**
   * Checks if the file is a Java source file or Java code fragment.
   *
   * @return true if the file is a Java source file or Java code fragment, false otherwise
   */
  boolean canContainJavaCode();

  /**
   * Returns the file type for the file.
   *
   * @return the file type instance.
   */
  @NotNull FileType getFileType();

  /**
   * If the file contains multiple interspersed languages, returns the roots for
   * PSI trees for each of these languages. (For example, a JSPX file contains JSP,
   * XML and Java trees.)
   *
   * @return the array of PSI roots, or a single-element array containing <code>this</code>
   * if the file has only a single language.
   */
  @NotNull PsiFile[] getPsiRoots();

  FileViewProvider getViewProvider();
}
