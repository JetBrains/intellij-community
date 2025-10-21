// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class JavaDirectoryService {
  public static JavaDirectoryService getInstance() {
    return ApplicationManager.getApplication().getService(JavaDirectoryService.class);
  }

  /**
   * Returns the package corresponding to the directory.
   *
   * @return the package instance, or null if the directory does not correspond to any package.
   */
  public abstract @Nullable PsiPackage getPackage(@NotNull PsiDirectory dir);

  /**
   * Returns the package corresponding to the directory.
   *
   * @return the package instance, or null if the directory does not correspond to any package or package is under resource roots
   */
  public abstract @Nullable PsiPackage getPackageInSources(@NotNull PsiDirectory dir);

  /**
   * Returns the list of Java classes contained in the directory.
   *
   * @return the array of classes.
   */
  public abstract PsiClass @NotNull [] getClasses(@NotNull PsiDirectory dir);

  /**
   * Returns the list of Java classes contained in the directory and the given {@param scope}.
   *
   * @return the array of classes.
   */
  @ApiStatus.Experimental
  public abstract PsiClass @NotNull [] getClasses(@NotNull PsiDirectory dir, @NotNull GlobalSearchScope scope);

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @param templateName custom file template to create class text based on.
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name, @NotNull String templateName) throws IncorrectOperationException;

  /**
   * @param askForUndefinedVariables
   *  true show dialog asking for undefined variables
   *  false leave them blank
   */
  public abstract PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name, @NotNull String templateName, boolean askForUndefinedVariables) throws IncorrectOperationException;

  /**
   * @param additionalProperties additional properties to be substituted in the template
   */
  public abstract PsiClass createClass(@NotNull PsiDirectory dir,
                                       @NotNull String name,
                                       @NotNull String templateName,
                                       boolean askForUndefinedVariables,
                                       final @NotNull Map<String, String> additionalProperties) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create a class with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the class to check creation possibility (not including the file extension).
   * @throws IncorrectOperationException if the creation is not possible.
   */
  public abstract void checkCreateClass(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an interface class with the specified name in the directory.
   *
   * @param name the name of the interface to create (not including the file extension).
   * @return the created interface instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createInterface(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an enumeration class with the specified name in the directory.
   *
   * @param name the name of the enumeration class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createEnum(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a record class with the specified name in the directory.
   *
   * @param name the name of the record class to create (not including the file extension).
   * @return the created record instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createRecord(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an annotation class with the specified name in the directory.
   *
   * @param name the name of the annotation class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  public abstract @NotNull PsiClass createAnnotationType(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;


  /**
   * Checks if the directory is a source root for the project to which it belongs.
   *
   * @return true if the directory is a source root, false otherwise
   */
  public abstract boolean isSourceRoot(@NotNull PsiDirectory dir);

  public abstract LanguageLevel getLanguageLevel(@NotNull PsiDirectory dir);
}