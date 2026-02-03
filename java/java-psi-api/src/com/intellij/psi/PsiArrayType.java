// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.lang.jvm.types.JvmArrayType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.JavaTypeNullabilityUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an array type.
 *
 * @author max
 */
public class PsiArrayType extends PsiType.Stub implements JvmArrayType {
  private final PsiType myComponentType;
  TypeNullability myNullability;
  @Nullable
  final PsiModifierListOwner myContainerNullabilityContext;

  public PsiArrayType(@NotNull PsiType componentType) {
    this(componentType, TypeAnnotationProvider.EMPTY);
  }

  public PsiArrayType(@NotNull PsiType componentType, PsiAnnotation @NotNull [] annotations) {
    this(componentType, TypeAnnotationProvider.Static.create(annotations));
  }

  public PsiArrayType(@NotNull PsiType componentType, @NotNull TypeAnnotationProvider provider) {
    this(componentType, provider, null, null);
  }

  PsiArrayType(@NotNull PsiType componentType,
               @NotNull TypeAnnotationProvider provider,
               @Nullable TypeNullability nullability,
               @Nullable PsiModifierListOwner containerNullabilityContext) {
    super(provider);
    myComponentType = componentType;
    myNullability = nullability;
    myContainerNullabilityContext = containerNullabilityContext;
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    return getText(getDeepComponentType().getPresentableText(annotated), "[]", false, annotated);
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return getText(getDeepComponentType().getCanonicalText(annotated), "[]", true, annotated);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    return getText(getDeepComponentType().getInternalCanonicalText(), "[]", true, true);
  }

  protected String getText(@NotNull String prefix, @NotNull String suffix, boolean qualified, boolean annotated) {
    int dimensions = getArrayDimensions();
    StringBuilder sb = new StringBuilder(prefix.length() + (dimensions - 1) * 2 + suffix.length());
    sb.append(prefix);
    PsiType current = this;
    for (int i = 0; i < dimensions; i++) {
      if (annotated) {
        PsiAnnotation[] annotations = current.getAnnotations();
        if (annotations.length != 0) {
          sb.append(' ');
          PsiNameHelper.appendAnnotations(sb, annotations, qualified);
        }
      }
      if (i == dimensions - 1) {
        sb.append(suffix);
      } else {
        sb.append("[]");
        current = ((PsiArrayType)current).getComponentType();
      }
    }
    return sb.toString();
  }

  @Override
  public boolean isValid() {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (!annotation.isValid()) return false;
    }
    return myComponentType.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.endsWith("[]") && myComponentType.equalsToText(text.substring(0, text.length() - 2));
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitArrayType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myComponentType.getResolveScope();
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    final PsiType[] superTypes = myComponentType.getSuperTypes();
    final PsiType[] result = createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      result[i] = superTypes[i].createArrayType();
    }
    return result;
  }

  /**
   * Returns the component type of the array.
   *
   * @return the component type instance.
   */
  @Override
  @Contract(pure = true)
  public @NotNull PsiType getComponentType() {
    return myComponentType;
  }

  /**
   * Creates a new array type instance with the specified nullable container nullability.
   *
   * @param containerNullabilityContext the PSI element pointer representing the context, or null if no specific context is required.
   * @return a new instance of {@link PsiType} with the specified nullable container nullability.
   */
  @ApiStatus.Experimental
  @NotNull
  public PsiType withContainerNullability(@Nullable PsiModifierListOwner containerNullabilityContext) {
    if (containerNullabilityContext == myContainerNullabilityContext) return this;
    return new PsiArrayType(myComponentType, getAnnotationProvider(), myNullability, containerNullabilityContext);
  }

  @ApiStatus.Experimental
  @Nullable
  static TypeNullability getContainerNullability(@NotNull PsiElement psiContext) {
    Project project = psiContext.getProject();
    if (project.isDefault()) return null;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager == null) return null;
    NullabilityAnnotationInfo defaultNullability = manager.findDefaultTypeUseNullability(psiContext);
    TypeNullability nullability = defaultNullability == null ? null : defaultNullability.toTypeNullability();
    return nullability;
  }

  /**
   * Creates a new array type instance with the specified nullable container nullability.
   *
   * @param arrayType the array type from which container nullability will be taken.
   * @return a new instance of {@link PsiType} with the specified nullable container nullability.
   */
  @ApiStatus.Experimental
  @NotNull
  public PsiType withContainerNullability(@Nullable PsiArrayType arrayType) {
    if (arrayType == null && myContainerNullabilityContext == null) return this;
    if (arrayType != null && arrayType.myContainerNullabilityContext == myContainerNullabilityContext) return this;
    return new PsiArrayType(myComponentType, getAnnotationProvider(), myNullability,
                            arrayType != null ? arrayType.myContainerNullabilityContext : null);
  }

  @Override
  public @NotNull TypeNullability getNullability() {
    if (myNullability == null) {
      TypeNullability nullability = JavaTypeNullabilityUtil.getNullabilityFromAnnotations(getAnnotations());
      if (nullability == TypeNullability.UNKNOWN && myContainerNullabilityContext != null) {
        TypeNullability containerNullability = getContainerNullability(myContainerNullabilityContext);
        if (containerNullability != null) {
          nullability = containerNullability;
        }
      }
      myNullability = nullability;
    }
    return myNullability;
  }

  /**
   * Creates a new array type with the given nullability.
   * @param nullability wanted nullability.
   * @return new array type instance.
   */
  @Override
  public @NotNull PsiArrayType withNullability(@NotNull TypeNullability nullability) {
    return new PsiArrayType(getComponentType(), getAnnotationProvider(), nullability, myContainerNullabilityContext);
  }

  @Override
  public @NotNull PsiArrayType annotate(@NotNull TypeAnnotationProvider provider) {
    PsiArrayType annotated = (PsiArrayType)super.annotate(provider);
    if (annotated != this) {
      annotated.myNullability = null;
    }
    return annotated;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PsiArrayType &&
           (this instanceof PsiEllipsisType == obj instanceof PsiEllipsisType) &&
           myComponentType.equals(((PsiArrayType)obj).getComponentType());
  }

  @Override
  public int hashCode() {
    return myComponentType.hashCode() * 3;
  }
}