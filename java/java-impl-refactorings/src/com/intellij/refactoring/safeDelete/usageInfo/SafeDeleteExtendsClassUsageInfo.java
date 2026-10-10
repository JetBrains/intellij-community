// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SafeDeleteExtendsClassUsageInfo extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {
  private static final Logger LOG = Logger.getInstance(SafeDeleteExtendsClassUsageInfo.class);
  private final SmartPsiElementPointer<PsiClass> myExtendingClass;

  public SafeDeleteExtendsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass extendingClass) {
    super(reference, refClass, true);
    myExtendingClass = SmartPointerManager.createPointer(extendingClass);
  }

  @Override
  public @Nullable PsiClass findReferencedElement() {
    return (PsiClass)super.findReferencedElement();
  }

  @Override
  public void deleteElement() {
    PsiElement element = getElement();
    PsiClass refClass = findReferencedElement();
    PsiClass extendingClass = myExtendingClass.getElement();
    if (element == null || refClass == null || extendingClass == null) return;
    deleteElement(element, refClass, extendingClass, true);
  }

  /**
   * @param updatePermits whether to write the permits lists of the other classes, see {@link #sealedPermitsAdditions()}: the fill of a
   *                      sealed supertype and the removal of the extending class from the permits of the deleted class
   */
  private static void deleteElement(@NotNull PsiElement element,
                                    @NotNull PsiClass refClass,
                                    @NotNull PsiClass extendingClass,
                                    boolean updatePermits) {
    final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(refClass, extendingClass, PsiSubstitutor.EMPTY);
    if (!LOG.assertTrue(substitutor != null)) return;
    final PsiElement parent = element.getParent();
    LOG.assertTrue(parent instanceof PsiReferenceList);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(refClass.getProject());

    element.delete();
    boolean targetTypeParameter = extendingClass instanceof PsiTypeParameter;
    copyExtendsList(refClass, refClass.getExtendsList(), extendingClass, substitutor,
                    refClass.isInterface() == extendingClass.isInterface() || targetTypeParameter, elementFactory, updatePermits);
    copyExtendsList(refClass, refClass.getImplementsList(), extendingClass, substitutor, targetTypeParameter, elementFactory,
                    updatePermits);


    if (!refClass.hasModifierProperty(PsiModifier.SEALED)) return;
    if (updatePermits) SealedUtils.removeFromPermitsList(refClass, extendingClass);
    final PsiModifierList modifiers = extendingClass.getModifierList();
    if (modifiers == null || !modifiers.hasModifierProperty(PsiModifier.NON_SEALED)) return;
    if (!SealedUtils.hasSealedParent(extendingClass)) {
      modifiers.setModifierProperty(PsiModifier.NON_SEALED, false);
    }
  }

  private static void copyExtendsList(@NotNull PsiClass classToRemove,
                                      @Nullable PsiReferenceList sourceExtendsList,
                                      @NotNull PsiClass extendingClass,
                                      @NotNull PsiSubstitutor substitutor,
                                      boolean targetExtends,
                                      PsiElementFactory elementFactory,
                                      boolean updatePermits) {
    if (sourceExtendsList != null) {
      final PsiReferenceList targetExtendsList = targetExtends ? extendingClass.getExtendsList() : extendingClass.getImplementsList();
      assert targetExtendsList != null;
      for (PsiClassType referenceType : sourceExtendsList.getReferencedTypes()) {
        PsiClass resolved = referenceType.resolve();
        if (resolved != null && extendingClass.isInheritor(resolved, true)) continue;
        PsiClassType classType = (PsiClassType)substitutor.substitute(referenceType);
        PsiElement extendsRef = targetExtendsList.add(elementFactory.createReferenceElementByType(classType));
        PsiClass classToExtend = classType.resolve();
        if (updatePermits && classToExtend != null && classToExtend.hasModifierProperty(PsiModifier.SEALED)) {
          String extendingClassName = Objects.requireNonNull(extendingClass.getQualifiedName());
          if (classToExtend.getPermitsList() == null) {
            if (classToExtend.getContainingFile() != extendingClass.getContainingFile()) {
              Collection<String> missingInheritors = new ArrayList<>(SealedUtils.findSameFileInheritors(classToExtend, classToRemove));
              missingInheritors.add(extendingClassName);
              SealedUtils.fillPermitsList(classToExtend, missingInheritors);
            }
          }
          else {
            SealedUtils.fillPermitsList(classToExtend, Collections.singleton(extendingClassName));
          }
        }
        CodeStyleManager.getInstance(extendingClass.getProject()).reformat(extendsRef);
      }
    }
  }

  @Override
  public boolean isSafeDelete() {
    final PsiClass classToRemove = findReferencedElement();
    final PsiClass extendingClass = myExtendingClass.getElement();
    if (getElement() == null || classToRemove == null || extendingClass == null) return false;
    if (classToRemove.getExtendsListTypes().length > 0) {
      final PsiReferenceList listToAddExtends = classToRemove.isInterface() == extendingClass.isInterface() ?
                                                extendingClass.getExtendsList() :
                                                extendingClass.getImplementsList();
      if (listToAddExtends == null) return false;
    }

    if (classToRemove.getImplementsListTypes().length > 0) {
      if (extendingClass.getImplementsList() == null) return false;
    }

    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(getProject());
    return StreamEx.of(classToRemove.getInterfaces()).prepend(classToRemove.getSuperClass()).nonNull()
      .allMatch(grandParent -> resolveHelper.isAccessible(grandParent, extendingClass, null));
  }

  /**
   * Writes the extending class on the copies of the reference and of the extending class; the deleted class joins them if it is in the
   * same file, because the usage resolves the reference to it. It does not write the permits lists of the other classes:
   * {@link SafeDeleteSealedPermitsUsageInfo} adds to them, and {@link SafeDeletePermitsClassUsageInfo} removes from them, each in its own
   * edit. The edit is refused, with null, if the extending class is a class that is not Java PSI in a physical file.
   */
  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    if (!(getElement() instanceof PsiJavaCodeReferenceElement reference)) return null;
    PsiClass deletedClass = findReferencedElement();
    PsiClass extendingClass = myExtendingClass.getElement();
    if (deletedClass == null || extendingClass == null || !isPhysicalJava(extendingClass)) return null;
    boolean sameFile = deletedClass.getContainingFile() == extendingClass.getContainingFile();
    List<PsiElement> elements = sameFile ? List.of(reference, extendingClass, deletedClass) : List.of(reference, extendingClass);
    SmartPsiElementPointer<PsiClass> deletedClassPointer = SmartPointerManager.createPointer(deletedClass);
    return ModPlanEdit.update(elements, copies -> {
      // The copy of another file resolves its references to the live deleted class, so only the live class gives the substitutor.
      PsiClass deletedCopy = sameFile ? (PsiClass)copies.get(2) : deletedClassPointer.getElement();
      if (deletedCopy != null) deleteElement(copies.get(0), deletedCopy, (PsiClass)copies.get(1), false);
    });
  }

  /**
   * Finds on the originals what the legacy {@link #deleteElement()} adds to the permits list of a sealed supertype of the deleted class:
   * the copy of its extends list skips a supertype that the extending class already inherits, from its other supertypes or from the ones
   * copied before. A class that gets the supertype joins its permits list: the list exists, the extending class only; no list, and
   * another file, the same-file inheritors of the supertype and the extending class; no list, and the same file, nothing.
   *
   * @return the names that join the permits list, by the sealed supertype
   */
  @NotNull Map<PsiClass, Collection<String>> sealedPermitsAdditions() {
    PsiClass extendingClass = myExtendingClass.getElement();
    String extendingName = extendingClass == null ? null : extendingClass.getQualifiedName();
    PsiClass deletedClass = findReferencedElement();
    if (extendingName == null || deletedClass == null) return Map.of();
    List<PsiClass> inheritedClasses = new ArrayList<>();
    for (PsiClassType type : ArrayUtil.mergeArrays(extendingClass.getExtendsListTypes(), extendingClass.getImplementsListTypes())) {
      PsiClass resolved = type.resolve();
      if (resolved != null && resolved != deletedClass) inheritedClasses.add(resolved);
    }
    Map<PsiClass, Collection<String>> resultNames = new LinkedHashMap<>();
    for (PsiReferenceList list : new PsiReferenceList[]{deletedClass.getExtendsList(), deletedClass.getImplementsList()}) {
      if (list == null) continue;
      for (PsiClassType type : list.getReferencedTypes()) {
        PsiClass supertype = type.resolve();
        if (supertype == null ||
            ContainerUtil.exists(inheritedClasses, base -> base == supertype || base.isInheritor(supertype, true))) {
          continue;
        }
        inheritedClasses.add(supertype);
        if (!supertype.hasModifierProperty(PsiModifier.SEALED)) continue;
        if (supertype.getPermitsList() != null) {
          resultNames.put(supertype, List.of(extendingName));
        }
        else if (supertype.getContainingFile() != extendingClass.getContainingFile()) {
          Collection<String> names = new ArrayList<>(SealedUtils.findSameFileInheritors(supertype, deletedClass));
          names.add(extendingName);
          resultNames.put(supertype, names);
        }
      }
    }
    return resultNames;
  }

  static boolean isPhysicalJava(@NotNull PsiClass aClass) {
    return aClass.isPhysical() && aClass.getNavigationElement() == aClass && aClass.getContainingFile() instanceof PsiJavaFile;
  }
}
