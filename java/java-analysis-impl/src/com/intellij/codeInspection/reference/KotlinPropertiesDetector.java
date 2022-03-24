// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to detect kotlin properties.
 * It's needed to connect additional edges between reference nodes during reference graph construction.
 */
public class KotlinPropertiesDetector {
  private final @NotNull RefManager myRefManager;
  private final @NotNull Map<UElement, UElement> myFieldsByAccessors;

  KotlinPropertiesDetector(@NotNull RefManager refManager, @NotNull UClass uClass) {
    myRefManager = refManager;
    MultiMap<PsiElement, UElement> elementsByProperties = new MultiMap<>(new LinkedHashMap<>());
    PsiElement psiClass = uClass.getSourcePsi();
    ContainerUtil.filter(uClass.getFields(), field -> isProperty(field)).forEach(
      field -> elementsByProperties.putValue(field.getSourcePsi(), field));
    boolean isCompanion = psiClass instanceof PsiNameIdentifierOwner && "Companion".equals(((PsiNameIdentifierOwner)psiClass).getName());
    if (isCompanion) {
      UClass containingClass = ObjectUtils.tryCast(UDeclarationKt.getContainingDeclaration(uClass), UClass.class);
      if (containingClass != null) {
        ContainerUtil.filter(containingClass.getFields(), field -> isProperty(field)).forEach(
          field -> elementsByProperties.putValue(field.getSourcePsi(), field));
      }
    }
    ContainerUtil.filter(uClass.getMethods(), method -> isPropertyOrAccessor(method)).forEach(sourcePsi -> {
      PsiElement property = sourcePsi.getSourcePsi();
      if (property != null && isPropertyAccessor(sourcePsi)) {
        property = property.getParent();
      }
      elementsByProperties.putValue(property, sourcePsi);
    });
    Map<UElement, UElement> fieldsByAccessors = new HashMap<>();
    for (var entry : elementsByProperties.entrySet()) {
      List<UElement> elements = new SmartList<>(entry.getValue());
      UElement first = elements.get(0);
      List<UElement> accessors;
      UElement backingField;
      if (first instanceof UField) {
        if (elements.size() > 1) {
          accessors = elements.subList(1, elements.size());
          backingField = first;
        }
        else {
          continue;
        }
      }
      else {
        continue;
      }
      accessors.forEach(accessor -> fieldsByAccessors.put(accessor, backingField));
    }
    myFieldsByAccessors = fieldsByAccessors;
  }

  /**
   * Set backing field for ref methods and accessors for ref fields for further references processing.
   */
  void setupProperties(@NotNull List<RefEntity> entities) {
    if (myFieldsByAccessors.isEmpty()) return;
    for (RefEntity child : entities) {
      if (!(child instanceof RefMethodImpl)) continue;
      RefMethodImpl refAccessor = (RefMethodImpl)child;
      UDeclaration uAccessor = refAccessor.getUastElement();
      UElement uBackingField = myFieldsByAccessors.get(uAccessor);
      if (uBackingField == null) continue;
      RefFieldImpl refBackingField = ObjectUtils.tryCast(myRefManager.getReference(uBackingField.getJavaPsi()), RefFieldImpl.class);
      if (refBackingField == null) continue;
      if (isProperty(uAccessor)) {
        boolean forReading = refAccessor.getName().startsWith("get");
        refAccessor.addReference(refBackingField, uBackingField.getSourcePsi(), refAccessor.getUastElement(), !forReading, forReading,
                                 null);
      }
      refBackingField.addAccessor(refAccessor);
      refAccessor.setBackingField(refBackingField);
    }
  }

  public static boolean isBackingFieldReference(@NotNull USimpleNameReferenceExpression node) {
    return node.getIdentifier().equals("field");
  }

  public static boolean isPropertyOrAccessor(@Nullable UElement uElement) {
    return isProperty(uElement, "KtProperty", "KtPropertyAccessor");
  }

  public static @Nullable PsiElement getPropertyElement(@NotNull UElement uElement) {
    return isPropertyOrAccessor(uElement) ? uElement.getJavaPsi() : uElement.getSourcePsi();
  }

  private static boolean isProperty(@Nullable UElement uElement) {
    return isProperty(uElement, "KtProperty");
  }

  private static boolean isPropertyAccessor(@Nullable UElement uElement) {
    return isProperty(uElement, "KtPropertyAccessor");
  }

  private static boolean isProperty(@Nullable UElement uElement, String... classNames) {
    // temporarily disabled kotlin props
    return false;
    //if (uElement == null) return false;
    //if (!(uElement instanceof UMethod) && !(uElement instanceof UField)) return false;
    //PsiElement javaPsi = uElement.getJavaPsi();
    //if (javaPsi instanceof PsiField && PsiUtil.isCompileTimeConstant((PsiField)javaPsi)) return false;
    //PsiElement sourcePsi = uElement.getSourcePsi();
    //if (sourcePsi == null) return false;
    //return ContainerUtil.exists(classNames, className -> className.equals(sourcePsi.getClass().getSimpleName()));
  }
}
