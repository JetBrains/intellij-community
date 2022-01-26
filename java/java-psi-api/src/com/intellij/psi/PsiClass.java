// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Represents a Java class or interface.
 *
 * @see PsiJavaFile#getClasses()
 */
public interface PsiClass
  extends PsiNameIdentifierOwner, PsiModifierListOwner, PsiDocCommentOwner, PsiTypeParameterListOwner,
          PsiQualifiedNamedElement, PsiTarget, PomRenameableTarget<PsiElement>, JvmClass {
  /**
   * The empty array of PSI classes which can be reused to avoid unnecessary allocations.
   */
  PsiClass @NotNull [] EMPTY_ARRAY = new PsiClass[0];

  ArrayFactory<PsiClass> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiClass[count];

  /**
   * Returns the fully qualified name of the class.
   *
   * @return the qualified name of the class, or null for anonymous and local classes, and for type parameters
   */
  @Override
  @Nullable @NlsSafe
  String getQualifiedName();

  /**
   * Checks if the class is an interface.
   *
   * @return true if the class is an interface, false otherwise.
   */
  boolean isInterface();

  /**
   * Checks if the class is an annotation type.
   *
   * @return true if the class is an annotation type, false otherwise
   */
  boolean isAnnotationType();

  /**
   * Checks if the class is an enumeration.
   *
   * @return true if the class is an enumeration, false otherwise.
   */
  boolean isEnum();

  /**
   * Checks if the class is a record.
   *
   * @return true if the class is an record, false otherwise.
   */
  default boolean isRecord() {
    return false;
  }

  /**
   * Returns the list of classes that this class or interface extends.
   *
   * @return the extends list, or null for anonymous classes.
   */
  @Nullable
  PsiReferenceList getExtendsList();

  /**
   * Returns the list of interfaces that this class implements.
   *
   * @return the implements list, or null for anonymous classes
   */
  @Nullable
  PsiReferenceList getImplementsList();

  /**
   * Returns the list of class types for the classes that this class or interface extends.
   *
   * @return the list of extended class types, or an empty list for anonymous classes.
   */
  PsiClassType @NotNull [] getExtendsListTypes();

  /**
   * Returns the list of class types for the interfaces that this class implements.
   *
   * @return the list of extended class types, or an empty list for anonymous classes,
   * enums and annotation types
   */
  PsiClassType @NotNull [] getImplementsListTypes();

  /**
   * Returns the list of classes that this class or interface permits.
   *
   * @return the permits list.
   */
  @Nullable
  @ApiStatus.Experimental
  default PsiReferenceList getPermitsList() {
    return null;
  }

  /**
   * Returns the list of class types that this class or interface explicitly permits.
   *
   * @return the list of explicitly permitted classes.
   */
  @ApiStatus.Experimental
  default PsiClassType @NotNull [] getPermitsListTypes() {
    PsiReferenceList permitsList = getPermitsList();
    if (permitsList != null) {
      return permitsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  /**
   * Returns the base class of this class.
   *
   * @return the base class. May return null when jdk is not configured, so no java.lang.Object is found,
   * or for java.lang.Object itself
   */
  @Nullable
  PsiClass getSuperClass();

  /**
   * Returns the list of interfaces implemented by the class, or extended by the interface.
   *
   * @return the list of interfaces.
   */
  PsiClass @NotNull [] getInterfaces();

  /**
   * Returns the list of classes and interfaces extended or implemented by the class.
   *
   * @return the list of classes or interfaces. May return zero elements when jdk is
   * not configured, so no java.lang.Object is found
   */
  PsiClass @NotNull [] getSupers();

  /**
   * Returns the list of class types for the classes and interfaces extended or
   * implemented by the class.
   *
   * @return the list of class types for the classes or interfaces.
   * For the class with no explicit extends list, the returned list always contains at least one element for the java.lang.Object type.
   * If psiClass is java.lang.Object, returned list is empty.
   */
  PsiClassType @NotNull [] getSuperTypes();

  /**
   * Returns the list of fields in the class.
   *
   * @return the list of fields.
   */
  @Override
  PsiField @NotNull [] getFields();

  /**
   * Returns the list of methods in the class.
   *
   * @return the list of methods.
   */
  @Override
  PsiMethod @NotNull [] getMethods();

  /**
   * Returns the list of constructors for the class.
   *
   * @return the list of constructors,
   */
  PsiMethod @NotNull [] getConstructors();

  /**
   * Returns the list of inner classes for the class.
   *
   * @return the list of inner classes.
   */
  @Override
  PsiClass @NotNull [] getInnerClasses();

  /**
   * Returns the list of class initializers for the class.
   *
   * @return the list of class initializers.
   */
  PsiClassInitializer @NotNull [] getInitializers();

  /**
   * Returns the list of fields in the class and all its superclasses.
   *
   * @return the list of fields.
   */
  PsiField @NotNull [] getAllFields();

  /**
   * Returns the list of methods in the class and all its superclasses.
   *
   * @return the list of methods.
   */
  PsiMethod @NotNull [] getAllMethods();

  /**
   * Returns the list of inner classes for the class and all its superclasses.
   *
   * @return the list of inner classes.
   */
  PsiClass @NotNull [] getAllInnerClasses();

  /**
   * Searches the class (and optionally its superclasses) for the field with the specified name.
   *
   * @param name       the name of the field to find.
   * @param checkBases if true, the field is also searched in the base classes of the class.
   * @return the field instance, or null if the field cannot be found.
   */
  @Nullable
  PsiField findFieldByName(@NonNls String name, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the method with
   * the signature matching the signature of the specified method.
   *
   * @param patternMethod the method used as a pattern for the search.
   * @param checkBases    if true, the method is also searched in the base classes of the class.
   * @return the method instance, or null if the method cannot be found.
   */
  @Nullable
  PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the methods with the signature
   * matching the signature of the specified method. If the superclasses are not searched,
   * the method returns multiple results only in case of a syntax error (duplicate method).
   *
   * @param patternMethod the method used as a pattern for the search.
   * @param checkBases    if true, the method is also searched in the base classes of the class.
   * @return the found methods, or an empty array if no methods are found.
   */
  PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases);

  @Override
  default JvmMethod @NotNull [] findMethodsByName(@NotNull String methodName) {
    return findMethodsByName(methodName, false);
  }

  /**
   * Searches the class (and optionally its superclasses) for the methods with the specified name.
   *
   * @param name       the name of the methods to find.
   * @param checkBases if true, the methods are also searched in the base classes of the class.
   * @return the found methods, or an empty array if no methods are found.
   */
  PsiMethod @NotNull [] findMethodsByName(@NonNls String name, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the methods with the specified name
   * and returns the methods along with their substitutors.
   *
   * @param name       the name of the methods to find.
   * @param checkBases if true, the methods are also searched in the base classes of the class.
   * @return the found methods and their substitutors, or an empty list if no methods are found.
   */
  @NotNull
  List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases);

  /**
   * Returns the list of methods in the class and all its superclasses, along with their
   * substitutors.
   *
   * @return the list of methods and their substitutors
   */
  @NotNull
  List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors();

  /**
   * Searches the class (and optionally its superclasses) for the inner class with the specified name.
   *
   * @param name       the name of the inner class to find.
   * @param checkBases if true, the inner class is also searched in the base classes of the class.
   * @return the inner class instance, or null if the inner class cannot be found.
   */
  @Nullable
  PsiClass findInnerClassByName(@NonNls String name, boolean checkBases);

  /**
   * Returns the token representing the opening curly brace of the class.
   *
   * @return the token instance, or null if the token is missing in the source code file.
   */
  @Nullable
  PsiElement getLBrace();

  /**
   * Returns the token representing the closing curly brace of the class.
   *
   * @return the token instance, or null if the token is missing in the source code file.
   */
  @Nullable
  PsiElement getRBrace();

  /**
   * Returns the name identifier of the class.
   *
   * @return the name identifier, or null if the class is anonymous or synthetic jsp class
   */
  @Override
  @Nullable
  PsiIdentifier getNameIdentifier();

  /**
   * Returns the PSI member in which the class has been declared (for example,
   * the method containing the anonymous inner class, or the file containing a regular
   * class, or the class owning a type parameter).
   *
   * @return the member in which the class has been declared.
   */
  PsiElement getScope();

  /**
   * Checks if this class is an inheritor of the specified base class.
   * Only java inheritance rules are considered.
   * Note that {@link com.intellij.psi.search.searches.ClassInheritorsSearch}
   * may return classes that are inheritors in broader, e.g. in ejb sense, but not in java sense.
   *
   * @param baseClass the base class to check the inheritance.
   * @param checkDeep if false, only direct inheritance is checked; if true, the base class is
   *                  searched in the entire inheritance chain
   * @return true if the class is an inheritor, false otherwise
   */
  boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep);

  /**
   * Checks if this class is a deep inheritor of the specified base class possibly bypassing a class
   * when checking inheritance chain.
   * Only java inheritance rules are considered.
   * Note that {@link com.intellij.psi.search.searches.ClassInheritorsSearch}
   * may return classes that are inheritors in broader, e.g. in ejb sense, but not in java sense.
   *
   * @param baseClass     the base class to check the inheritance.
   *                      searched in the entire inheritance chain
   * @param classToByPass class to bypass the inheritance check for
   * @return true if the class is an inheritor, false otherwise
   */
  boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass);

  /**
   * For an inner class, returns its containing class.
   *
   * @return the containing class, or null if the class is not an inner class.
   */
  @Override
  @Nullable
  PsiClass getContainingClass();

  /**
   * Returns the hierarchical signatures for all methods in the specified class and
   * its superclasses and superinterfaces.
   *
   * @return the collection of signatures.
   */
  @NotNull
  Collection<HierarchicalMethodSignature> getVisibleSignatures();

  @Override
  PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException;

  @NotNull
  @Override
  default JvmClassKind getClassKind() {
    return PsiJvmConversionHelper.getJvmClassKind(this);
  }

  @Nullable
  @Override
  default JvmReferenceType getSuperClassType() {
    return PsiJvmConversionHelper.getClassSuperType(this);
  }

  @Override
  default JvmReferenceType @NotNull [] getInterfaceTypes() {
    return PsiJvmConversionHelper.getClassInterfaces(this);
  }

  default PsiRecordComponent @NotNull [] getRecordComponents() {
    return PsiRecordComponent.EMPTY_ARRAY;
  }

  @Nullable
  default PsiRecordHeader getRecordHeader() {
    return null;
  }
}