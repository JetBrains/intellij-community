/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaDirectoryService {
  public static JavaDirectoryService getInstance() {
    return ServiceManager.getService(JavaDirectoryService.class);
  }

  /**
   * Returns the package corresponding to the directory.
   *
   * @return the package instance, or null if the directory does not correspond to any package.
   */
  @Nullable
  public abstract PsiPackage getPackage(@NotNull PsiDirectory dir);

  /**
   * Returns the list of Java classes contained in the directory.
   *
   * @return the array of classes.
   */
  @NotNull
  public abstract PsiClass[] getClasses(@NotNull PsiDirectory dir);

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull
  public abstract PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @param templateName custom file template to create class text based on.
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   * @since 5.1
   */
  @NotNull
  public abstract PsiClass createClass(@NotNull PsiDirectory dir, @NotNull String name, @NotNull String templateName) throws IncorrectOperationException;

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
  @NotNull
  public abstract PsiClass createInterface(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create an interface with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the interface to check creation possibility (not including the file extension).
   * @throws IncorrectOperationException if the creation is not possible.
   */
  public abstract void checkCreateInterface(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an enumeration class with the specified name in the directory.
   *
   * @param name the name of the enumeration class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull
  public abstract PsiClass createEnum(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an annotation class with the specified name in the directory.
   *
   * @param name the name of the annotation class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull
  public abstract PsiClass createAnnotationType(@NotNull PsiDirectory dir, @NotNull String name) throws IncorrectOperationException;


  /**
   * Checks if the directory is a source root for the project to which it belongs.
   *
   * @return true if the directory is a source root, false otherwise
   */
  public abstract boolean isSourceRoot(@NotNull PsiDirectory dir);

  public abstract LanguageLevel getLanguageLevel(@NotNull PsiDirectory dir);
}