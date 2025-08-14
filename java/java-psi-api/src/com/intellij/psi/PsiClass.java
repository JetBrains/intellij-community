// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

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
   * @return true if the class is a record, false otherwise.
   */
  default boolean isRecord() {
    return false;
  }

  /**
   * Checks if the class is a Valhalla value class.
   *
   * @return true if the class is a value class, false otherwise.
   */
  default boolean isValueClass() {
    return false;
  }

  /**
   * Returns the list of classes that this class or interface extends.
   *
   * @return the extends list, or null for anonymous classes and unnamed classes.
   */
  @Nullable
  PsiReferenceList getExtendsList();

  /**
   * Returns the list of interfaces that this class implements.
   *
   * @return the implements list, or null for anonymous classes and unnamed classes
   */
  @Nullable
  PsiReferenceList getImplementsList();

  /**
   * Returns the array of class types for the classes that this class or interface extends.
   *
   * @return the array of extended class types, or an empty list for anonymous classes and implicitly declared classes.
   */
  PsiClassType @NotNull [] getExtendsListTypes();

  /**
   * Returns the array of class types for the interfaces that this class implements.
   *
   * @return the array of extended class types, or an empty list for anonymous classes,
   * enums, annotation types and implicitly declared classes
   */
  PsiClassType @NotNull [] getImplementsListTypes();

  /**
   * Returns the list of classes that this class or interface explicitly permits.
   *
   * @return the permits list, or null if there's none.
   */
  default @Nullable PsiReferenceList getPermitsList() {
    return null;
  }

  /**
   * Returns the array of class types that this class or interface explicitly permits.
   *
   * @return the array of explicitly permitted classes.
   */
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
   * Returns the array of interfaces implemented by the class, or extended by the interface.
   *
   * @return the array of interfaces.
   */
  PsiClass @NotNull [] getInterfaces();

  /**
   * Returns the array of classes and interfaces extended or implemented by the class.
   *
   * @return the array of classes or interfaces. May return zero elements when jdk is
   * not configured, so no java.lang.Object is found
   */
  PsiClass @NotNull [] getSupers();

  /**
   * Returns the array of class types for the classes and interfaces extended or
   * implemented by the class.
   *
   * @return the array of class types for the classes or interfaces.
   * For the class with no explicit extends list, the returned list always contains at least one element for the java.lang.Object type.
   * If psiClass is java.lang.Object, returned list is empty.
   */
  PsiClassType @NotNull [] getSuperTypes();

  /**
   * Returns the array of fields in the class.
   *
   * @return the array of fields.
   */
  @Override
  PsiField @NotNull [] getFields();

  /**
   * Returns the array of methods in the class.
   *
   * @return the array of methods.
   */
  @Override
  PsiMethod @NotNull [] getMethods();

  /**
   * Returns the array of constructors for the class.
   *
   * @return the array of constructors,
   */
  PsiMethod @NotNull [] getConstructors();

  /**
   * Returns the array of (static and non-static) nested classes for the class.
   *
   * @return the array of (static and non-static) nested classes.
   */
  @Override
  PsiClass @NotNull [] getInnerClasses();

  /**
   * Returns the array of class initializers for the class.
   *
   * @return the array of class initializers.
   */
  PsiClassInitializer @NotNull [] getInitializers();

  /**
   * Returns the array of fields in the class and all its superclasses.
   *
   * @return the array of fields.
   */
  PsiField @NotNull [] getAllFields();

  /**
   * Returns the array of methods in the class and all its superclasses.
   *
   * @return the array of methods.
   */
  PsiMethod @NotNull [] getAllMethods();

  /**
   * Returns the array of (static and non-static) nested classes for the class and all its superclasses.
   *
   * @return the array of (static and non-static) nested classes.
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
  @Unmodifiable
  List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases);

  /**
   * Returns the list of methods in the class and all its superclasses, along with their
   * substitutors.
   *
   * @return the list of methods and their substitutors
   */
  @NotNull
  @Unmodifiable
  List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors();

  /**
   * Searches the class (and optionally its superclasses) for the (static or non-static) nested class with the specified name.
   *
   * @param name       the name of the (static or non-static) nested class to find.
   * @param checkBases if true, the nested class is also searched in the base classes of the class.
   * @return the nested class instance, or null if the nested class cannot be found.
   */
  @Nullable
  PsiClass findInnerClassByName(@NonNls String name, boolean checkBases);

  /**
   * Returns the token representing the opening curly brace of the class.
   *
   * @return the token instance, or null if the token is absent in the source code file.
   */
  @Nullable
  PsiElement getLBrace();

  /**
   * Returns the token representing the closing curly brace of the class.
   *
   * @return the token instance, or null if the token is absent in the source code file.
   */
  @Nullable
  PsiElement getRBrace();

  /**
   * Returns the name identifier of the class.
   *
   * @return the name identifier, or null if the class is anonymous, synthetic jsp class or unnamed class
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
   * For a (static or non-static) nested class, returns its containing class.
   *
   * @return the containing class, or null if the class is not a (static or non-static) nested class.
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

  @Override
  default @NotNull JvmClassKind getClassKind() {
    return PsiJvmConversionHelper.getJvmClassKind(this);
  }

  @Override
  default @Nullable JvmReferenceType getSuperClassType() {
    return PsiJvmConversionHelper.getClassSuperType(this);
  }

  @Override
  default JvmReferenceType @NotNull [] getInterfaceTypes() {
    return PsiJvmConversionHelper.getClassInterfaces(this);
  }

  default PsiRecordComponent @NotNull [] getRecordComponents() {
    return PsiRecordComponent.EMPTY_ARRAY;
  }

  default @Nullable PsiRecordHeader getRecordHeader() {
    return null;
  }
}