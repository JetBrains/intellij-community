/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaPsiFacade {
  public static JavaPsiFacade getInstance(Project project) {
    return ServiceManager.getService(project, JavaPsiFacade.class);
  }


  /**
   * Searches the project and all its libraries for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @return the PSI class, or null if no class with such name is found.
   * @deprecated use {@link #findClass(String, com.intellij.psi.search.GlobalSearchScope)}
   */
  @Nullable
  public abstract PsiClass findClass(@NotNull @NonNls String qualifiedName);


  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   */
  @Nullable
  public abstract PsiClass findClass(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);


  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   */
  @NotNull
  public abstract PsiClass[] findClasses(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and returns one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   */
  @Nullable
  public abstract PsiPackage findPackage(@NonNls @NotNull String qualifiedName);

  /**
   * Returns the element factory for the project, which can be used to
   * create instances of Java and XML PSI elements.
   *
   * @return the element factory instance.
   */
  @NotNull
  public abstract PsiElementFactory getElementFactory();

  /**
   * Returns the factory for the project, which can be used to create instances of certain java constructs from their textual
   * presentation. Elements created shall not be used to later interfer (like insert into) a PSI parsed from the user codebase
   * since no formatting to the user codestyle will be performed in this case. Please use {@link #getElementFactory()} instead, which
   * provides exactly same methods but ensures created instances will get properly formatted.
   * @return the parser facade.
   */
  @NotNull
  public abstract PsiJavaParserFacade getParserFacade();

  /**
   * Returns the resolve helper for the project, which can be used to resolve references
   * and check accessibility of elements.
   *
   * @return the resolve helper instance.
   */
  @NotNull
  public abstract PsiResolveHelper getResolveHelper();

  /**
   * Returns the short name cache for the project, which can be used to locate files, classes,
   * methods and fields by non-qualified names.
   *
   * @return the short name cache instance.
   */
  @NotNull
  public abstract PsiShortNamesCache getShortNamesCache();

  /**
   * Registers a custom short name cache implementation for the project, which is used
   * in addition to the standard IDEA implementation. Should not be used by most plugins.
   *
   * @param cache the short name cache instance.
   */
  public abstract void registerShortNamesCache(@NotNull PsiShortNamesCache cache);

  /**
   * Initiates a migrate refactoring. The refactoring is finished when
   * {@link com.intellij.psi.PsiMigration#finish()} is called.
   *
   * @return the migrate operation object.
   */
  @NotNull
  public abstract PsiMigration startMigration();

  /**
   * Returns the JavaDoc manager for the project, which can be used to retrieve
   * information about JavaDoc tags known to IDEA.
   *
   * @return the JavaDoc manager instance.
   */
  @NotNull
  public abstract JavadocManager getJavadocManager();

  /**
   * Returns the name helper for the project, which can be used to validate
   * and parse Java identifiers.
   *
   * @return the name helper instance.
   */
  @NotNull
  public abstract PsiNameHelper getNameHelper();

  /**
   * Returns the constant expression evaluator for the project.
   *
   * @return the evaluator instance.
   */
  @NotNull
  public abstract PsiConstantEvaluationHelper getConstantEvaluationHelper();

  /**
   * Returns the language level set for this project.
   *
   * @deprecated  use {@link com.intellij.psi.PsiJavaFile#getLanguageLevel()} or
   * {@link com.intellij.psi.util.PsiUtil#getLanguageLevel(PsiElement)}
   * @return the language level instance.
   */
  @NotNull
  public abstract LanguageLevel getEffectiveLanguageLevel();

  /**
   * Checks if the specified package name is part of the package prefix for
   * any of the modules in this project.
   *
   * @param packageName the package name to check.
   * @return true if it is part of the package prefix, false otherwise.
   */
  public abstract boolean isPartOfPackagePrefix(String packageName);

  /**
   * Sets the language level to use for this project. For tests only.
   *
   * @param languageLevel the language level to set.
   */
  public abstract void setEffectiveLanguageLevel(@NotNull LanguageLevel languageLevel);

  /* Checks if the specified PSI element belongs to the specified package.
   *
   * @param element  the element to check the package for.
   * @param aPackage the package to check.
   * @return true if the element belongs to the package, false otherwise.
   */
  public abstract boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage);

  /**
   * Checks if the specified PSI elements belong to the same package.
   *
   * @param element1 the first element to check.
   * @param element2 the second element to check.
   * @return true if the elements are in the same package, false otherwise.
   */
  public abstract boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2);


  /**
   * Finds GUI Designer forms bound to the specified class.
   *
   * @param className the fully-qualified name of the class to find bound forms for.
   * @return the array of found bound forms.
   */
  @NotNull
  public abstract PsiFile[] findFormsBoundToClass(String className);

  /**
   * Checks if the specified field is bound to a GUI Designer form component.
   *
   * @param field the field to check the binding for.
   * @return true if the field is bound, false otherwise.
   */
  public abstract boolean isFieldBoundToForm(@NotNull PsiField field);
}