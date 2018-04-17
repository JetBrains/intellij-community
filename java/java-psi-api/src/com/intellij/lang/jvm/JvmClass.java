// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.util.JvmClassDefaults;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JvmClass extends JvmTypeParametersOwner, JvmTypeDeclaration {

  /**
   * @return the name, or {@code null} if the class is anonymous
   * @see Class#getSimpleName
   */
  @Nullable
  @NonNls
  @Override
  String getName();

  /**
   * @return the qualified name, of {@code null} if the class is anonymous or local
   * @see Class#getCanonicalName
   */
  @Nullable
  @NonNls
  String getQualifiedName();

  @NotNull
  JvmClassKind getClassKind();

  /**
   * @return direct super type or {@code null} if this class is an interface or represents {@link Object}
   * @see Class#getSuperclass
   * @see Class#getGenericSuperclass
   * @see Class#getAnnotatedSuperclass
   */
  @Nullable
  JvmReferenceType getSuperClassType();

  /**
   * @return interface types which are directly implemented by this class
   * @see Class#getInterfaces
   * @see Class#getAnnotatedInterfaces
   * @see Class#getGenericInterfaces
   */
  @NotNull
  JvmReferenceType[] getInterfaceTypes();

  //

  /**
   * @return all (static, private, etc) methods and constructors declared by this class but excluding inherited ones
   * @see Class#getDeclaredMethods
   * @see Class#getDeclaredConstructors
   */
  @NotNull
  JvmMethod[] getMethods();

  /**
   * @return methods (excluding inherited) with the specified name
   * @see Class#getDeclaredMethod
   */
  @NotNull
  default JvmMethod[] findMethodsByName(@NotNull String methodName) {
    return JvmClassDefaults.findMethodsByName(this, methodName);
  }

  /**
   * @return all (static, private, etc) fields declared by this class but excluding inherited ones
   * @see Class#getDeclaredFields
   */
  @NotNull
  JvmField[] getFields();

  /**
   * @return all (static, private, etc) inner classes declared by this class but excluding inherited ones
   * @see Class#getDeclaredClasses
   */
  @NotNull
  JvmClass[] getInnerClasses();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitClass(this);
  }
}
