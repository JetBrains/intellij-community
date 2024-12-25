// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ClsModifierListImpl extends ClsRepositoryPsiElement<PsiModifierListStub> implements PsiModifierList {
  public ClsModifierListImpl(PsiModifierListStub stub) {
    super(stub);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return getAnnotations();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return ModifierFlags.hasModifierProperty(name, getStub().getModifiersMask());
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return hasModifierProperty(name);
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getStub().getChildrenByType(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw cannotModifyException(this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    PsiElement parent = getParent();
    PsiAnnotation[] annotations = getAnnotations();
    boolean separateAnnotations =
      parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiField || parent instanceof PsiJavaModule;

    for (PsiAnnotation annotation : annotations) {
      appendText(annotation, indentLevel, buffer, separateAnnotations ? NEXT_LINE : " ");
    }

    boolean isClass = parent instanceof PsiClass;
    boolean isInterface = isClass && ((PsiClass)parent).isInterface();
    boolean isEnum = isClass && ((PsiClass)parent).isEnum();
    boolean isInterfaceClass = isClass && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    boolean isMethod = parent instanceof PsiMethod;
    boolean isInterfaceMethod = isMethod && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    boolean isField = parent instanceof PsiField;
    boolean isInterfaceField = isField && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    boolean isEnumConstant = parent instanceof PsiEnumConstant;

    if (hasModifierProperty(PsiModifier.PUBLIC) && !isInterfaceMethod && !isInterfaceField && !isInterfaceClass && !isEnumConstant) {
      buffer.append(PsiModifier.PUBLIC).append(' ');
    }
    if (hasModifierProperty(PsiModifier.PROTECTED)) {
      buffer.append(PsiModifier.PROTECTED).append(' ');
    }
    if (hasModifierProperty(PsiModifier.PRIVATE)) {
      buffer.append(PsiModifier.PRIVATE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.STATIC) && !isInterfaceField && !isEnumConstant) {
      buffer.append(PsiModifier.STATIC).append(' ');
    }
    if (hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface && !isInterfaceMethod) {
      buffer.append(PsiModifier.ABSTRACT).append(' ');
    }
    if (hasModifierProperty(PsiModifier.FINAL) && !isEnum && !isInterfaceField && !isEnumConstant) {
      buffer.append(PsiModifier.FINAL).append(' ');
    }
    if (hasModifierProperty(PsiModifier.SEALED)) {
      buffer.append(PsiModifier.SEALED).append(' ');
    }
    if (hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(PsiModifier.NATIVE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      buffer.append(PsiModifier.SYNCHRONIZED).append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSIENT)) {
      buffer.append(PsiModifier.TRANSIENT).append(' ');
    }
    if (hasModifierProperty(PsiModifier.VOLATILE)) {
      buffer.append(PsiModifier.VOLATILE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.STRICTFP)) {
      buffer.append(PsiModifier.STRICTFP).append(' ');
    }
    if (hasModifierProperty(PsiModifier.DEFAULT)) {
      buffer.append(PsiModifier.DEFAULT).append(' ');
    }
    if (hasModifierProperty(PsiModifier.OPEN)) {
      buffer.append(PsiModifier.OPEN).append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSITIVE)) {
      buffer.append(PsiModifier.TRANSITIVE).append(' ');
    }
  }

  @Override
  public String getText() {
    StringBuilder builder = new StringBuilder();
    appendMirrorText(0, builder);
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ') {
      builder.setLength(builder.length() - 1);
    }
    return builder.toString();
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODIFIER_LIST);
    PsiAnnotation[] annotations = getAnnotations();
    PsiAnnotation[] mirrorAnnotations = SourceTreeToPsiMap.<PsiModifierList>treeToPsiNotNull(element).getAnnotations();
    // Annotations could be inconsistent, as in stubs all type annotations are attached to the types
    // not to modifier list
    Map<String, PsiAnnotation> annotationByShortName = getAnnotationByShortName(annotations);
    Map<String, PsiAnnotation> mirrorAnnotationByShortName = getAnnotationByShortName(mirrorAnnotations);
    if (!annotationByShortName.containsKey(null) &&
        !mirrorAnnotationByShortName.containsKey(null) &&
        annotationByShortName.size() == annotations.length &&
        mirrorAnnotationByShortName.size() == mirrorAnnotations.length) {
      //it is possible to work with short name without resolving
      for (Map.Entry<String, PsiAnnotation> annotationEntry : annotationByShortName.entrySet()) {
        String key = annotationEntry.getKey();
        PsiAnnotation mirror = mirrorAnnotationByShortName.get(key);
        if (mirror != null) {
          PsiAnnotation annotation = annotationEntry.getValue();
          setMirror(annotation, mirror);
        }
      }
      return;
    }
    DumbService.getInstance(getProject()).runWithAlternativeResolveEnabled(() -> {
      //necessary to use AlternativeResolver, because of getQualifiedName()
      for (PsiAnnotation annotation : annotations) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null) {
          PsiAnnotation mirror = ContainerUtil.find(mirrorAnnotations, m -> qualifiedName.equals(m.getQualifiedName()));
          if (mirror != null) {
            setMirror(annotation, mirror);
          }
        }
      }
    });
  }

  private static @NotNull Map<String, PsiAnnotation> getAnnotationByShortName(@NotNull PsiAnnotation @NotNull [] annotations) {
    HashMap<String, PsiAnnotation> result = new HashMap<>();
    for (@NotNull PsiAnnotation annotation : annotations) {
      result.put(getAnnotationReferenceShortName(annotation), annotation);
    }
    return result;
  }

  private static @Nullable String getAnnotationReferenceShortName(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement == null) return null;
    return referenceElement.getReferenceName();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiModifierList";
  }
}
