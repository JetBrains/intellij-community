// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.AnnotationUtil.*;

/**
 * @author anna
 */
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);
  protected final Project myProject;

  protected NullableNotNullManager(Project project) {
    myProject = project;
  }

  /**
   * @return list of default non-container annotations that apply to the nullable element
   */
  @ApiStatus.Internal
  public abstract @NotNull List<String> getDefaultNullables();

  /**
   * @return list of default non-container annotations that apply to the not-null element
   */
  @ApiStatus.Internal
  public abstract @NotNull List<String> getDefaultNotNulls();

  public abstract @NotNull Optional<Nullability> getAnnotationNullability(String name);

  public abstract boolean isTypeUseAnnotationLocationRestricted(String name);

  public abstract boolean canAnnotateLocals(String name);

  public static NullableNotNullManager getInstance(Project project) {
    return project.getService(NullableNotNullManager.class);
  }

  public abstract void setNotNulls(String @NotNull ... annotations);

  public abstract void setNullables(String @NotNull ... annotations);

  public abstract @NotNull String getDefaultNullable();

  public abstract void setDefaultNullable(@NotNull String defaultNullable);

  public abstract @NotNull String getDefaultNotNull();

  public void copyNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NOT_NULL) return;
    copyAnnotation(info.getAnnotation(), generated);
  }

  public @Nullable PsiAnnotation copyNullableAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NULLABLE) return null;
    return copyAnnotation(info.getAnnotation(), generated);
  }

  public @Nullable PsiAnnotation copyNullableOrNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo src = findOwnNullabilityInfo(original);
    if (src == null) return null;
    NullabilityAnnotationInfo effective = findEffectiveNullabilityInfo(generated);
    if (effective != null && effective.getNullability() == src.getNullability()) return null;
    return copyAnnotation(src.getAnnotation(), generated);
  }

  private static @Nullable PsiAnnotation copyAnnotation(@NotNull PsiAnnotation annotation, @NotNull PsiModifierListOwner target) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName != null) {
      if (JavaPsiFacade.getInstance(annotation.getProject()).findClass(qualifiedName, target.getResolveScope()) == null) {
        return null;
      }

      PsiModifierList modifierList = target.getModifierList();
      PsiType usedType = null;
      if(target instanceof PsiVariable){
        usedType = ((PsiVariable)target).getType();
      }
      else if (target instanceof PsiMethod) {
        usedType = ((PsiMethod)target).getReturnType();
      }
      // type annotations are part of target's type and should not to be copied explicitly to avoid duplication
      if (modifierList != null &&
          (!AnnotationTargetUtil.isStrictlyTypeUseAnnotation(modifierList, annotation) ||
           (usedType != null && !usedType.hasAnnotation(qualifiedName))) &&
          !modifierList.hasAnnotation(qualifiedName)) {
        return modifierList.addAnnotation(qualifiedName);
      }
    }

    return null;
  }

  public abstract void setDefaultNotNull(@NotNull String defaultNotNull);

  /**
   * Returns own nullability annotation info for given element. Returned annotation is not inherited and
   * not container annotation for class/package. Still it could be inferred or external.
   *
   * @param owner element to find a nullability info for
   * @return own nullability annotation info.
   */
  public @Nullable NullabilityAnnotationInfo findOwnNullabilityInfo(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    if (info == null || info.isContainer() || info.getInheritedFrom() != null) return null;
    return info;
  }

  /**
   * Returns information about explicit nullability annotation (without looking into external/inferred annotations,
   * but looking into container annotations). This method is rarely useful in client code, it's designed mostly
   * to aid the inference procedure.
   *
   * @param owner element to get the info about
   * @return the annotation info or null if no explicit annotation found
   */
  public @Nullable NullabilityAnnotationInfo findExplicitNullability(PsiModifierListOwner owner) {
    NullabilityAnnotationInfo result = findPlainAnnotation(owner, true, getAllNullabilityAnnotationsWithNickNames());
    if (result != null) {
      return result;
    }
    return findContainerAnnotation(owner);
  }

  /**
   * Returns nullability annotation info which has effect for a given element.
   *
   * @param owner element to find an annotation for
   * @return effective nullability annotation info, or null if not found.
   */
  public abstract @Nullable NullabilityAnnotationInfo findEffectiveNullabilityInfo(@NotNull PsiModifierListOwner owner);

  protected final @Nullable NullabilityAnnotationInfo doFindEffectiveNullabilityAnnotation(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationDataHolder annotations = getAllNullabilityAnnotationsWithNickNames();
    NullabilityAnnotationInfo result = findPlainOrContainerAnnotation(owner, annotations);
    if (result != null) return result;

    if (owner instanceof PsiMethod) {
      for (PsiModifierListOwner superOwner : getSuperAnnotationOwners(owner)) {
        NullabilityAnnotationInfo superAnno = findPlainOrContainerAnnotation(superOwner, annotations);
        if (superAnno != null) {
          return superAnno.withInheritedFrom(superOwner);
        }
      }
    }

    if (owner instanceof PsiParameter) {
      List<PsiParameter> superParameters = getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        for (PsiParameter parameter: superParameters) {
          NullabilityAnnotationInfo plain = findPlainAnnotation(parameter, false, annotations);
          // Plain not null annotation is not inherited
          if (plain != null) return null;
          NullabilityAnnotationInfo defaultInfo = findContainerAnnotation(parameter);
          if (defaultInfo != null) {
            return defaultInfo.getNullability() == Nullability.NOT_NULL ? defaultInfo.withInheritedFrom(parameter) : null;
          }
        }
        return null;
      }
    }

    return null;
  }

  private @Nullable NullabilityAnnotationInfo findPlainOrContainerAnnotation(@NotNull PsiModifierListOwner owner,
                                                                             @NotNull NullabilityAnnotationDataHolder annotations) {
    NullabilityAnnotationInfo result = findPlainAnnotation(owner, false, annotations);
    if (result != null) {
      return result;
    }

    boolean lambdaParameter = owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList &&
                              owner.getParent().getParent() instanceof PsiLambdaExpression;

    if (!lambdaParameter) {
      // For lambda parameter, default annotation is ignored
      NullabilityAnnotationInfo defaultInfo = findNullityDefaultFiltered(owner);
      if (defaultInfo != null) return defaultInfo;
    }
    return null;
  }

  private @Nullable NullabilityAnnotationInfo findNullityDefaultFiltered(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo defaultInfo = findContainerAnnotation(owner);
    if (defaultInfo != null && (defaultInfo.getNullability() == Nullability.NULLABLE || !hasHardcodedContracts(owner))) {
      return defaultInfo;
    }
    return null;
  }

  /**
   * @return the annotation info (if any) with the given nullability semantics on the given declaration or its type. In case of conflicts,
   * type annotations are preferred.
   */
  public @Nullable NullabilityAnnotationInfo findNullabilityAnnotationInfo(@NotNull PsiModifierListOwner owner,
                                                                           @NotNull Collection<Nullability> nullabilities) {
    NullabilityAnnotationDataHolder holder = getAllNullabilityAnnotationsWithNickNames();
    Set<String> filteredSet =
      holder.qualifiedNames().stream().filter(qName -> nullabilities.contains(holder.getNullability(qName))).collect(Collectors.toSet());
    NullabilityAnnotationDataHolder filtered = new NullabilityAnnotationDataHolder() {
      @Override
      public Set<String> qualifiedNames() {
        return filteredSet;
      }

      @Override
      public @Nullable Nullability getNullability(String annotation) {
        Nullability origNullability = holder.getNullability(annotation);
        return nullabilities.contains(origNullability) ? origNullability : null;
      }
    };
    NullabilityAnnotationInfo result = findPlainAnnotation(owner, false, filtered);
    return result == null || !nullabilities.contains(result.getNullability()) ? null : result;
  }

  private @Nullable NullabilityAnnotationInfo findPlainAnnotation(
    @NotNull PsiModifierListOwner owner, boolean skipExternal, NullabilityAnnotationDataHolder annotations) {
    PsiAnnotation annotation = findAnnotation(owner, annotations.qualifiedNames(), skipExternal);
    AnnotationAndOwner memberAnno = annotation == null ? null : new AnnotationAndOwner(owner, annotation);
    PsiType type = PsiUtil.getTypeByPsiElement(owner);
    if (memberAnno != null && type instanceof PsiArrayType && !isInferredAnnotation(memberAnno.annotation) &&
        !isExternalAnnotation(memberAnno.annotation) && AnnotationTargetUtil.isTypeAnnotation(memberAnno.annotation)) {
      // Ambiguous TYPE_USE annotation on array type: we consider that it annotates an array component instead.
      // ignore inferred/external annotations here, as they are applicable to PsiModifierListOwner only, regardless of target
      memberAnno = null;
    }
    if (memberAnno != null) {
      Nullability nullability = annotations.getNullability(memberAnno.annotation.getQualifiedName());
      if (nullability == null) return null;
      nullability = correctNullability(nullability, memberAnno.annotation);
      if (type != null) {
        for (PsiAnnotation typeAnno : type.getApplicableAnnotations()) {
          if (typeAnno == memberAnno.annotation) continue;
          Nullability typeNullability = annotations.getNullability(typeAnno.getQualifiedName());
          if (typeNullability == null) continue;
          if (typeNullability != nullability) {
            return null;
          }
          // Prefer type annotation over inherited annotation; necessary for Nullable/NotNull inspection
          memberAnno = new AnnotationAndOwner(owner, typeAnno);
          break;
        }
      }
      return new NullabilityAnnotationInfo(memberAnno.annotation, nullability, memberAnno.owner == owner ? null : memberAnno.owner, false);
    }
    if (type instanceof PsiPrimitiveType) return null;
    NullabilityAnnotationInfo inHierarchy = findAnnotationInTypeHierarchy(type, annotations);
    if (inHierarchy != null && 
        owner instanceof PsiLocalVariable && 
        !canAnnotateLocals(inHierarchy.getAnnotation().getQualifiedName())) {
      return null;
    }
    return inHierarchy;
  }

  protected @NotNull Nullability correctNullability(@NotNull Nullability nullability, @NotNull PsiAnnotation annotation) {
    return nullability;
  }

  @ApiStatus.Internal
  @NotNull
  public List<String> getNullablesWithNickNames() {
    return getNullables();
  }

  @ApiStatus.Internal
  @NotNull
  public List<String> getNotNullsWithNickNames() {
    return getNotNulls();
  }

  protected abstract @NotNull NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames();

  protected boolean hasHardcodedContracts(@NotNull PsiElement element) {
    return false;
  }

  public boolean isNullable(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    return info != null && info.getNullability() == Nullability.NULLABLE && (checkBases || info.getInheritedFrom() == null);
  }

  public boolean isNotNull(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    NullabilityAnnotationInfo info = findEffectiveNullabilityInfo(owner);
    return info != null && info.getNullability() == Nullability.NOT_NULL && (checkBases || info.getInheritedFrom() == null);
  }

  /**
   * @param context place in PSI tree
   * @return default nullability for type-use elements at given place
   */
  public @Nullable NullabilityAnnotationInfo findDefaultTypeUseNullability(@Nullable PsiElement context) {
    if (context == null) return null;
    return findNullabilityDefault(context, PsiAnnotation.TargetType.TYPE_USE);
  }

  /**
   * Looks for applicable container annotation, ignoring explicit, inferred, external, or inherited annotations.
   * Usually, should not be used directly, as {@link #findEffectiveNullabilityInfo(PsiModifierListOwner)} will
   * return container annotation if it's applicable.
   *
   * @param owner member to find annotation for
   * @return container annotation applicable to the owner location
   */
  public @Nullable NullabilityAnnotationInfo findContainerAnnotation(@NotNull PsiModifierListOwner owner) {
    return findNullabilityDefault(owner, AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList()));
  }

  private @Nullable NullabilityAnnotationInfo findNullabilityDefault(@NotNull PsiElement place,
                                                                     @NotNull PsiAnnotation.TargetType @NotNull ... placeTargetTypes) {
    PsiElement element = place.getContext();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullabilityAnnotationInfo result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes, place, false);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        PsiPackage psiPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
        NullabilityAnnotationInfo fromPackage = findNullityDefaultOnPackage(placeTargetTypes, psiPackage, place);
        if (fromPackage != null) {
          return fromPackage;
        }
        return findNullityDefaultOnModule(placeTargetTypes, element);
      }

      element = element.getContext();
    }
    return null;
  }

  protected @Nullable NullabilityAnnotationInfo findNullityDefaultOnModule(PsiAnnotation.@NotNull TargetType @NotNull [] types,
                                                                           @NotNull PsiElement element) {
    return null;
  }

  private @Nullable NullabilityAnnotationInfo findNullityDefaultOnPackage(PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                          @Nullable PsiPackage psiPackage,
                                                                          PsiElement context) {
    boolean superPackage = false;
    while (psiPackage != null) {
      NullabilityAnnotationInfo onPkg = getNullityDefault(psiPackage, placeTargetTypes, context, superPackage);
      if (onPkg != null) return onPkg;
      superPackage = true;
      psiPackage = psiPackage.getParentPackage();
    }
    return null;
  }

  protected abstract @Nullable NullabilityAnnotationInfo getNullityDefault(@NotNull PsiModifierListOwner container,
                                                                           PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                           @NotNull PsiElement context, boolean superPackage);

  public abstract @NotNull List<String> getNullables();

  public abstract @NotNull List<String> getNotNulls();

  /**
   * Returns true if given element is known to be nullable
   *
   * @param owner element to check
   * @return true if given element is known to be nullable
   */
  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NULLABLE;
  }

  /**
   * Returns true if given element is known to be non-nullable
   *
   * @param owner element to check
   * @return true if given element is known to be non-nullable
   */
  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NOT_NULL;
  }

  /**
   * Returns nullability of given element defined by annotations.
   *
   * @param owner element to find nullability for
   * @return found nullability; {@link Nullability#UNKNOWN} if not specified or non-applicable
   */
  public static @NotNull Nullability getNullability(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
    return info == null ? Nullability.UNKNOWN : info.getNullability();
  }

  public abstract @Unmodifiable @NotNull List<String> getInstrumentedNotNulls();

  public abstract void setInstrumentedNotNulls(@NotNull List<String> names);

  /**
   * Checks if given annotation specifies the nullability (either nullable or not-null)
   * @param annotation annotation to check
   * @return true if given annotation specifies nullability
   */
  public static boolean isNullabilityAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getAllNullabilityAnnotationsWithNickNames()
             .getNullability(annotation.getQualifiedName()) != null;
  }

  /**
   * @param type type to check
   * @param qualifiedNames annotation qualified names of TYPE_USE annotations to look for
   * @return found type annotation, or null if not found. For type parameter types upper bound annotations are also checked
   */
  @Contract("null, _ -> null")
  private @Nullable NullabilityAnnotationInfo findAnnotationInTypeHierarchy(@Nullable PsiType type,
                                                                            @NotNull NullabilityAnnotationDataHolder qualifiedNames) {
    if (type == null) return null;
    Ref<NullabilityAnnotationInfo> result = Ref.create(null);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      for (PsiAnnotation annotation : eachType.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedNames.qualifiedNames().contains(qualifiedName)) {
          Nullability nullability = qualifiedNames.getNullability(qualifiedName);
          if (nullability != null) {
            nullability = correctNullability(nullability, annotation);
            result.set(new NullabilityAnnotationInfo(annotation, nullability, false));
          }
          return false;
        }
      }
      if (!(eachType instanceof PsiClassType)) return true;
      PsiClassType classType = (PsiClassType)eachType;
      PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(eachType);
      if (!(targetClass instanceof PsiTypeParameter)) return false;
      if (targetClass.getExtendsListTypes().length == 0) {
        PsiAnnotation.TargetType[] targetType;
        PsiModifierListOwner owner = getOwner(classType);
        if (owner != null) {
          targetType = AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList());
        } else {
          targetType = new PsiAnnotation.TargetType[]{PsiAnnotation.TargetType.TYPE_PARAMETER};
        }
        NullabilityAnnotationInfo info = findNullabilityDefault(targetClass, targetType);
        if (info != null) {
          result.set(info);
          return false;
        }
      }
      return true;
    });
    return result.get();
  }
  
  private static PsiModifierListOwner getOwner(@NotNull PsiClassType classType) {
    PsiJavaCodeReferenceElement context = ObjectUtils.tryCast(classType.getPsiContext(), PsiJavaCodeReferenceElement.class);
    if (context != null) {
      PsiTypeElement typeElement = ObjectUtils.tryCast(context.getParent(), PsiTypeElement.class);
      if (typeElement != null) {
        PsiModifierListOwner owner = ObjectUtils.tryCast(typeElement.getParent(), PsiModifierListOwner.class);
        return owner;
      }
    }
    return null;
  }

  protected interface NullabilityAnnotationDataHolder {
    /**
     * @return qualified names of all recognized annotations
     */
    Set<String> qualifiedNames();

    /**
     * @param annotation annotation qualified name to check
     * @return nullability
     */
    @Nullable Nullability getNullability(String annotation);

    /**
     * @param map from annotation qualified name to nullability
     * @return a data holder implementation based on the provided map
     */
    static @NotNull NullabilityAnnotationDataHolder fromMap(@NotNull Map<String, Nullability> map) {
      return new NullabilityAnnotationDataHolder() {
        @Override
        public Set<String> qualifiedNames() {
          return map.keySet();
        }
  
        @Override
        public @Nullable Nullability getNullability(String annotation) {
          return map.get(annotation);
        }
      };
    }
  }
}