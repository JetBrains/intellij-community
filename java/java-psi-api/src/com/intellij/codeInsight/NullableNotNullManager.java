// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.AnnotationUtil.*;

/**
 * @author anna
 */
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);
  private static final PsiAnnotation.@NotNull TargetType[] TYPE_USE_TARGET =
    new PsiAnnotation.TargetType[]{PsiAnnotation.TargetType.TYPE_USE};
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

  /**
   * @param nullability wanted nullability
   * @param context PSI context
   * @return the best suitable annotation to insert in a specified context
   */
  public abstract @NotNull String getDefaultAnnotation(@NotNull Nullability nullability, @NotNull PsiElement context);

  /**
   * @return default nullable annotation, 
   * used for external and inferred annotations, or as a fallback when no other annotation is available
   * @see #getDefaultAnnotation(Nullability, PsiElement)
   */
  public abstract @NotNull String getDefaultNullable();

  /**
   * Sets the default nullable annotation, used for external and inferred annotations, 
   * or as a fallback when no other annotation is available.
   * 
   * @param defaultNullable new default nullable annotation
   * @see #getDefaultNullable() 
   */
  public abstract void setDefaultNullable(@NotNull String defaultNullable);

  /**
   * @return default not-null annotation, 
   * used for external and inferred annotations, or as a fallback when no other annotation is available
   * @see #getDefaultAnnotation(Nullability, PsiElement) 
   */
  public abstract @NotNull String getDefaultNotNull();

  /**
   * Sets the default not-null annotation, used for external and inferred annotations, 
   * or as a fallback when no other annotation is available.
   *
   * @param defaultNotNull new default nullable annotation
   * @see #getDefaultNotNull()
   */
  public abstract void setDefaultNotNull(@NotNull String defaultNotNull);

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
    if (type == null || type instanceof PsiPrimitiveType) return null;
    NullabilityAnnotationInfo info = type.getNullability().toNullabilityAnnotationInfo();
    return info != null && annotations.getNullability(info.getAnnotation().getQualifiedName()) != null ? info : null;
  }

  protected @NotNull Nullability correctNullability(@NotNull Nullability nullability, @NotNull PsiAnnotation annotation) {
    return nullability;
  }

  protected abstract @NotNull ContextNullabilityInfo getNullityDefault(@NotNull PsiModifierListOwner container,
                                                                       PsiAnnotation.TargetType @NotNull [] placeTargetTypes);

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
    PsiElement element = context.getContext();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        PsiModifierListOwner listOwner = (PsiModifierListOwner)element;
        NullabilityAnnotationInfo result = CachedValuesManager.getCachedValue(listOwner, () -> {
          return CachedValueProvider.Result.create(getNullityDefault(listOwner, TYPE_USE_TARGET),
                                                   PsiModificationTracker.MODIFICATION_COUNT);
        }).forContext(context);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        PsiClassOwner classOwner = (PsiClassOwner)element;
        return CachedValuesManager.getCachedValue(classOwner, () -> {
          ContextNullabilityInfo fromPackage = findNullityDefaultOnPackage(TYPE_USE_TARGET, classOwner.getContainingFile());
          return CachedValueProvider.Result.create(fromPackage.orElse(findNullityDefaultOnModule(TYPE_USE_TARGET, classOwner)),
                                                   PsiModificationTracker.MODIFICATION_COUNT);
        }).forContext(context);
      }

      element = element.getContext();
    }
    return null;
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
    PsiElement element = place;
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullabilityAnnotationInfo result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes).forContext(place);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        NullabilityAnnotationInfo fromPackage = findNullityDefaultOnPackage(placeTargetTypes, element.getContainingFile()).forContext(place);
        if (fromPackage != null) {
          return fromPackage;
        }
        return findNullityDefaultOnModule(placeTargetTypes, element).forContext(place);
      }

      element = element.getContext();
    }
    return null;
  }

  protected @NotNull ContextNullabilityInfo findNullityDefaultOnModule(PsiAnnotation.@NotNull TargetType @NotNull [] types, 
                                                                       @NotNull PsiElement element) {
    return ContextNullabilityInfo.EMPTY;
  }

  protected @NotNull ContextNullabilityInfo findNullityDefaultOnPackage(PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                        PsiFile file) {
    return ContextNullabilityInfo.EMPTY;
  }

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