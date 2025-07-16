// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.*;
import java.util.stream.Stream;

@ApiStatus.NonExtendable
public class AnnotationUtil {
  public static final String NULLABLE = "org.jetbrains.annotations.Nullable";
  public static final String UNKNOWN_NULLABILITY = "org.jetbrains.annotations.UnknownNullability";
  public static final String NOT_NULL = "org.jetbrains.annotations.NotNull";
  public static final String NOT_NULL_BY_DEFAULT = "org.jetbrains.annotations.NotNullByDefault";

  public static final String NON_NLS = "org.jetbrains.annotations.NonNls";
  public static final String NLS = "org.jetbrains.annotations.Nls";

  public static final String PROPERTY_KEY = "org.jetbrains.annotations.PropertyKey";
  public static final String PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER = "resourceBundle";

  public static final String TEST_ONLY = "org.jetbrains.annotations.TestOnly";

  public static final String LANGUAGE = "org.intellij.lang.annotations.Language";

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, String @NotNull ... annotationNames) {
    return findAnnotation(listOwner, false, annotationNames);
  }

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, boolean skipExternal, String @NotNull ... annotationNames) {
    if (annotationNames.length == 0) return null;
    Set<String> set = annotationNames.length == 1 ? Collections.singleton(annotationNames[0]) : ContainerUtil.newHashSet(annotationNames);
    return findAnnotation(listOwner, set, skipExternal);
  }

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull @Unmodifiable Set<String> annotationNames) {
    return findAnnotation(listOwner, (Collection<String>)annotationNames);
  }

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull @Unmodifiable Collection<String> annotationNames) {
    return findAnnotation(listOwner, annotationNames, false);
  }

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull @Unmodifiable Collection<String> annotationNames, boolean skipExternal) {
    if (listOwner == null) return null;
    List<PsiAnnotation> result = findAllAnnotations(listOwner, annotationNames, skipExternal);
    return result.isEmpty() ? null : result.get(0);
  }

  /**
   * Returns all annotations associated with {@code listOwner} having fully qualified names from {@code annotationNames},
   * including repeatable annotations and annotations from several external annotations roots.
   *
   * @param listOwner element to search annotations of
   * @param annotationNames fully-qualified annotations names to search for
   * @param skipExternal {@code false} if external and inferred annotations must also be searched,
   * {@code true} only to search for own annotations declared in source code
   * @return all annotations of {@code listOwner}, including repeatable annotation
   * and annotations from several source roots, having FQ names from {@code annotationNames}.
   */
  public static @NotNull List<PsiAnnotation> findAllAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull @Unmodifiable Collection<String> annotationNames,
                                                       boolean skipExternal) {
    List<PsiAnnotation> ownAnnotations = findOwnAnnotations(listOwner, annotationNames);
    List<PsiAnnotation> nonCodeAnnotations = skipExternal ? null : findNonCodeAnnotations(listOwner, annotationNames);
    List<PsiAnnotation> annotations = null;
    if (ownAnnotations != null || nonCodeAnnotations != null) {
      annotations = new SmartList<>();
      if (ownAnnotations != null) {
        annotations.addAll(ownAnnotations);
      }
      if (nonCodeAnnotations != null) {
        annotations.addAll(nonCodeAnnotations);
      }
    }
    return annotations == null ? Collections.emptyList() : annotations;
  }

  private static @Nullable List<PsiAnnotation> findOwnAnnotations(final @NotNull PsiModifierListOwner listOwner, @NotNull Iterable<String> annotationNames) {
    final PsiModifierList list = listOwner.getModifierList();
    if (list == null) {
      return null;
    }
    List<PsiAnnotation> result = null;
    for (PsiAnnotation annotation : list.getAnnotations()) {
      if (ContainerUtil.exists(annotationNames, annotation::hasQualifiedName) && isApplicableToDeclaration(annotation, list)) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(annotation);
      }
    }
    return result;
  }

  private static boolean isApplicableToDeclaration(PsiAnnotation annotation, PsiModifierList list) {
    PsiAnnotation.TargetType[] allTargets = AnnotationTargetUtil.getTargetsForLocation(list);
    if (allTargets.length == 0) return true;

    PsiAnnotation.TargetType[] nonTypeUse = Stream
      .of(allTargets)
      .filter(t -> t != PsiAnnotation.TargetType.TYPE_USE)
      .toArray(PsiAnnotation.TargetType[]::new);
    return AnnotationTargetUtil.findAnnotationTarget(annotation, nonTypeUse) != null;
  }

  private static final ParameterizedCachedValueProvider<Map<Collection<String>, List<PsiAnnotation>>, PsiModifierListOwner> NON_CODE_ANNOTATIONS_PROVIDER =
    (PsiModifierListOwner listOwner) -> {
      Map<Collection<String>, List<PsiAnnotation>> value = ConcurrentFactoryMap.createMap(
        annotationNames -> {
          PsiUtilCore.ensureValid(listOwner);
          final Project project = listOwner.getProject();
          final ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);
          List<PsiAnnotation> externalAnnotations = externalAnnotationsManager.findExternalAnnotations(listOwner, annotationNames);

          final InferredAnnotationsManager inferredAnnotationsManager = InferredAnnotationsManager.getInstance(project);
          List<PsiAnnotation> inferredAnnotations = null;
          for (String annotationName : annotationNames) {
            final PsiAnnotation annotation = inferredAnnotationsManager.findInferredAnnotation(listOwner, annotationName);
            if (annotation != null) {
              if (inferredAnnotations == null) {
                inferredAnnotations = new SmartList<>();
              }
              inferredAnnotations.add(annotation);
            }
          }
          return inferredAnnotations == null ? externalAnnotations : ContainerUtil.concat(externalAnnotations, inferredAnnotations);
        }
      );
      return CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
    };

  private static final Key<ParameterizedCachedValue<Map<Collection<String>, List<PsiAnnotation>>, PsiModifierListOwner>> NON_CODE_ANNOTATIONS_KEY =
    Key.create("NON_CODE_ANNOTATIONS");

  private static @Nullable List<PsiAnnotation> findNonCodeAnnotations(@NotNull PsiModifierListOwner element,
                                                                      @NotNull @Unmodifiable Collection<String> annotationNames) {
    if (element instanceof PsiLocalVariable) {
      // Non-code annotations for local variables are not supported: don't bother to search them
      return null;
    }
    PsiModifierListOwner listOwner = AnnotationCacheOwnerNormalizer.normalize(element);

    Map<Collection<String>, List<PsiAnnotation>> map = CachedValuesManager.getManager(element.getProject())
      .getParameterizedCachedValue(
        listOwner,
        NON_CODE_ANNOTATIONS_KEY,
        NON_CODE_ANNOTATIONS_PROVIDER,
        false,
        listOwner
      );

    return map.get(annotationNames);
  }

  public static PsiAnnotation @NotNull [] findAnnotations(@Nullable PsiModifierListOwner modifierListOwner, @NotNull @Unmodifiable Collection<String> annotationNames) {
    if (modifierListOwner == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    ArrayList<PsiAnnotation> result = null;
    for (final PsiAnnotation psiAnnotation : annotations) {
      String qualifiedName = psiAnnotation.getQualifiedName();
      if (qualifiedName != null && annotationNames.contains(qualifiedName)) {
        if (result == null) result = new ArrayList<>();
        result.add(psiAnnotation);
      }
    }
    return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  public static @NotNull @Unmodifiable <T extends PsiModifierListOwner> List<T> getSuperAnnotationOwners(@NotNull T element) {
    PsiModifierListOwner listOwner = AnnotationCacheOwnerNormalizer.normalize(element);
    return CachedValuesManager.getCachedValue(listOwner, () -> {
      Set<PsiModifierListOwner> result = new LinkedHashSet<>();
      if (listOwner instanceof PsiMethod) {
        if (!listOwner.hasModifierProperty(PsiModifier.STATIC)) {
          collectSuperMethods(result, ((PsiMethod)listOwner).getHierarchicalMethodSignature(), listOwner,
                              JavaPsiFacade.getInstance(listOwner.getProject()).getResolveHelper());
        }
      }
      else if (listOwner instanceof PsiClass) {
        InheritanceUtil.processSupers((PsiClass)listOwner, false, Processors.cancelableCollectProcessor(result));
      }
      else if (listOwner instanceof PsiParameter) {
        collectSuperParameters(result, (PsiParameter)listOwner);
      }

      List<T> list;
      if(result.isEmpty()) {
        list = Collections.emptyList();
      }
      else {
        PsiModifierListOwner[] array = result.toArray(new PsiModifierListOwner[0]);
        //noinspection unchecked
        list = Arrays.asList((T[])array);
      }

      return CachedValueProvider.Result.create(list, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  public static @Nullable PsiAnnotation findAnnotationInHierarchy(final @NotNull PsiModifierListOwner listOwner, @NotNull @Unmodifiable Set<String> annotationNames) {
    return findAnnotationInHierarchy(listOwner, annotationNames, false);
  }

  public static @Nullable PsiAnnotation findAnnotationInHierarchy(final @NotNull PsiModifierListOwner listOwner,
                                                                  @NotNull @Unmodifiable Set<String> annotationNames, boolean skipExternal) {
    AnnotationAndOwner result = findAnnotationAndOwnerInHierarchy(listOwner, annotationNames, skipExternal);
    return result == null ? null : result.annotation;
  }

  static final class AnnotationAndOwner {
    final @NotNull PsiModifierListOwner owner;
    final @NotNull PsiAnnotation annotation;

    AnnotationAndOwner(@NotNull PsiModifierListOwner owner, @NotNull PsiAnnotation annotation) {
      this.owner = owner;
      this.annotation = annotation;
    }
  }

  private static @Nullable AnnotationAndOwner findAnnotationAndOwnerInHierarchy(@NotNull PsiModifierListOwner listOwner,
                                                                                @NotNull @Unmodifiable Set<String> annotationNames,
                                                                                boolean skipExternal) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames, skipExternal);
    if (directAnnotation != null) return new AnnotationAndOwner(listOwner, directAnnotation);

    for (PsiModifierListOwner superOwner : getSuperAnnotationOwners(listOwner)) {
      PsiAnnotation annotation = findAnnotation(superOwner, annotationNames, skipExternal);
      if (annotation != null) {
        return new AnnotationAndOwner(superOwner, annotation);
      }
    }
    return null;
  }

  private static void collectSuperParameters(final @NotNull Set<? super PsiModifierListOwner> result, @NotNull PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (!(parent instanceof PsiParameterList)) {
      return;
    }
    final int index = ((PsiParameterList)parent).getParameterIndex(parameter);
    Consumer<PsiMethod> forEachSuperMethod = method -> {
      PsiParameter[] superParameters = method.getParameterList().getParameters();
      if (index < superParameters.length) {
        result.add(superParameters[index]);
      }
    };

    PsiElement scope = parent.getParent();
    if (scope instanceof PsiLambdaExpression) {
      PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)scope).getFunctionalInterfaceType());
      if (method != null) {
        forEachSuperMethod.consume(method);
        for (PsiMethod superMethod : getSuperAnnotationOwners(method)) {
          forEachSuperMethod.consume(superMethod);
        }
      }
    }
    else if (scope instanceof PsiMethod) {
      for (PsiMethod superMethod : getSuperAnnotationOwners((PsiMethod)scope)) {
        forEachSuperMethod.consume(superMethod);
      }
    }
  }

  private static void collectSuperMethods(@NotNull Set<? super PsiModifierListOwner> result,
                                          @NotNull HierarchicalMethodSignature signature,
                                          @NotNull PsiElement place,
                                          @NotNull PsiResolveHelper resolveHelper) {
    for (final HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
      final PsiMethod superMethod = superSignature.getMethod();
      if (!resolveHelper.isAccessible(superMethod, place, null)) continue;
      if (!result.add(superMethod)) continue;
      collectSuperMethods(result, superSignature, place, resolveHelper);
    }
  }

  public static final int CHECK_HIERARCHY = 0x01;
  public static final int CHECK_EXTERNAL = 0x02;
  public static final int CHECK_INFERRED = 0x04;
  public static final int CHECK_TYPE = 0x08;

  @MagicConstant(flags = {CHECK_HIERARCHY, CHECK_EXTERNAL, CHECK_INFERRED, CHECK_TYPE})
  @Target({ElementType.PARAMETER, ElementType.METHOD})
  private @interface Flags { }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull @Unmodifiable Collection<String> annotations, @Flags int flags) {
    return ContainerUtil.exists(annotations, annotation -> isAnnotated(listOwner, annotation, flags, null));
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFqn, @Flags int flags) {
    return isAnnotated(listOwner, annotationFqn, flags, null);
  }

  private static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN, @Flags int flags, @Nullable Set<? super PsiMember> processed) {
    PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList == null) return false;

    PsiAnnotation annotation = modifierList.findAnnotation(annotationFQN);
    if (annotation != null) return true;

    if (BitUtil.isSet(flags, CHECK_TYPE)) {
      PsiType type = null;
      if (listOwner instanceof PsiMethod) {
        type = ((PsiMethod)listOwner).getReturnType();
      }
      else if (listOwner instanceof PsiParameter &&
               listOwner.getParent() instanceof PsiParameterList &&
               listOwner.getParent().getParent() instanceof PsiLambdaExpression) {
        if (((PsiParameter)listOwner).getTypeElement() != null) {
          // Avoid lambda parameter type inference: anyway it doesn't have any explicit annotations
          type = ((PsiParameter)listOwner).getType();
        }
      }
      else if (listOwner instanceof PsiVariable) {
        type = ((PsiVariable)listOwner).getType();
      }
      if (type != null && type.hasAnnotation(annotationFQN)) {
        return true;
      }
    }

    if (BitUtil.isSet(flags, CHECK_EXTERNAL)) {
      Project project = listOwner.getProject();
      if (ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(listOwner, annotationFQN) != null) {
        return true;
      }
    }

    if (BitUtil.isSet(flags, CHECK_INFERRED)) {
      Project project = listOwner.getProject();
      if (InferredAnnotationsManager.getInstance(project).findInferredAnnotation(listOwner, annotationFQN) != null) {
        return true;
      }
    }

    if (BitUtil.isSet(flags, CHECK_HIERARCHY)) {
      if (listOwner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)listOwner;
        if (processed == null) {
          processed = new HashSet<>();
        }
        if (!processed.add(method)) {
          return false;
        }
        for (PsiMethod superMethod : method.findSuperMethods()) {
          if (isAnnotated(superMethod, annotationFQN, flags, processed)) {
            return true;
          }
        }
      }
      else if (listOwner instanceof PsiClass) {
        PsiClass clazz = (PsiClass)listOwner;
        if (processed == null) {
          processed = new HashSet<>();
        }
        if (!processed.add(clazz)) {
          return false;
        }
        for (PsiClass superClass : clazz.getSupers()) {
          if (isAnnotated(superClass, annotationFQN, flags, processed)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt, @NotNull String annotationFQN) {
    final Project project = elt.getProject();
    return PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, elt) &&
           JavaPsiFacade.getInstance(project).findClass(annotationFQN, elt.getResolveScope()) != null;
  }

  /**
   * Works similar to #isAnnotated(PsiModifierListOwner, Collection<String>) but supports FQN patters
   * like "javax.ws.rs.*". Supports ending "*" only.
   *
   * @param owner modifier list
   * @param annotations annotations qualified names or patterns. Patterns can have '*' at the end
   * @return {@code true} if annotated of at least one annotation from the annotations list
   */
  @Contract("null,_ -> false")
  public static boolean checkAnnotatedUsingPatterns(@Nullable PsiModifierListOwner owner, @NotNull @Unmodifiable Collection<String> annotations) {
    final PsiModifierList modList;
    if (owner == null || (modList = owner.getModifierList()) == null) return false;

    List<String> fqns = null;
    for (String fqn : annotations) {
      boolean isPattern = fqn.endsWith("*");
      if (!isPattern && isAnnotated(owner, fqn, 0)) {
        return true;
      }
      else if (isPattern) {
        if (fqns == null) {
          fqns = new ArrayList<>();
          final PsiAnnotation[] ownAnnotations = modList.getAnnotations();
          for (PsiAnnotation anno : ownAnnotations) {
            final String qName = anno.getQualifiedName();
            if (qName != null) {
              fqns.add(qName);
            }
          }
          if (fqns.isEmpty()) return false;
        }
        fqn = fqn.substring(0, fqn.length() - 2);
        for (String annoFQN : fqns) {
          if (annoFQN.startsWith(fqn)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static @Nullable PsiMethod getAnnotationMethod(@NotNull PsiNameValuePair pair) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair.getParent(), PsiAnnotation.class);
    assert annotation != null;

    final String fqn = annotation.getQualifiedName();
    if (fqn == null) return null;

    final PsiClass psiClass = JavaPsiFacade.getInstance(pair.getProject()).findClass(fqn, pair.getResolveScope());
    if (psiClass != null && psiClass.isAnnotationType()) {
      final String name = pair.getName();
      return ArrayUtil.getFirstElement(psiClass.findMethodsByName(name != null ? name : "value", false));
    }
    return null;
  }

  public static PsiAnnotation @NotNull [] getAllAnnotations(@NotNull PsiModifierListOwner owner,
                                                            boolean inHierarchy,
                                                            @Nullable Set<? super PsiModifierListOwner> visited) {
    return getAllAnnotations(owner, inHierarchy, visited, true);
  }

  public static PsiAnnotation @NotNull [] getAllAnnotations(@NotNull PsiModifierListOwner owner,
                                                            boolean inHierarchy,
                                                            @Nullable Set<? super PsiModifierListOwner> visited, boolean withInferred) {
    final PsiModifierList list = owner.getModifierList();
    PsiAnnotation[] annotations = PsiAnnotation.EMPTY_ARRAY;
    if (list != null) {
      annotations = list.getAnnotations();
    }

    final Project project = owner.getProject();
    final PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(owner);
    annotations = ArrayUtil.mergeArrays(annotations, externalAnnotations, PsiAnnotation.ARRAY_FACTORY);
    if (withInferred) {
      final PsiAnnotation[] inferredAnnotations = InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner);
      annotations = ArrayUtil.mergeArrays(annotations, inferredAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }

    if (inHierarchy) {
      if (owner instanceof PsiClass) {
        for (PsiClass superClass : ((PsiClass)owner).getSupers()) {
          if (visited == null) {
            visited = new HashSet<>();
          }
          if (visited.add(superClass)) annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superClass, true, visited, withInferred));
        }
      }
      else if (owner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)owner;
        PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

          final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
          PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(aClass.getProject());
          for (final HierarchicalMethodSignature superSignature : superSignatures) {
            final PsiMethod superMethod = superSignature.getMethod();
            if (visited == null) {
              visited = new HashSet<>();
            }
            if (!visited.add(superMethod) || !resolveHelper.isAccessible(superMethod, owner, null)) {
              continue;
            }
            annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superMethod, true, visited, withInferred));
          }
        }
      }
      else if (owner instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)owner;
        PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)scope;
          PsiClass aClass = method.getContainingClass();
          PsiElement parent = parameter.getParent();
          if (aClass != null && parent instanceof PsiParameterList) {
            int index = ((PsiParameterList)parent).getParameterIndex(parameter);
            HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

            final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
            PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(aClass.getProject());
            for (final HierarchicalMethodSignature superSignature : superSignatures) {
              final PsiMethod superMethod = superSignature.getMethod();
              if (visited == null) {
                visited = new HashSet<>();
              }
              if (!visited.add(superMethod) || !resolveHelper.isAccessible(superMethod, owner, null)) {
                continue;
              }
              PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
              if (index < superParameters.length) {
                annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superParameters[index], true, visited, withInferred));
              }
            }
          }
        }
      }
    }
    return annotations;
  }

  public static boolean isInsideAnnotation(@NotNull PsiElement element) {
    for (int level = 0; level<4; level++) {
      if (element instanceof PsiNameValuePair) return true;
      element = element.getParent();
      if (element == null) return false;
    }
    return false;
  }

  public static boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return InferredAnnotationsManager.getInstance(annotation.getProject()).isInferredAnnotation(annotation);
  }

  public static boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
    return ExternalAnnotationsManager.getInstance(annotation.getProject()).isExternalAnnotation(annotation);
  }

  public static @Nullable @NlsSafe String getStringAttributeValue(@NotNull PsiAnnotation anno, final @Nullable String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    return attrValue == null ? null : getStringAttributeValue(attrValue);
  }

  public static @Nullable Boolean getBooleanAttributeValue(@NotNull PsiAnnotation anno, final @Nullable String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof Boolean ? (Boolean)constValue : null;
  }

  public static @Nullable Long getLongAttributeValue(@NotNull PsiAnnotation anno, final @Nullable String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof Number ? ((Number)constValue).longValue() : null;
  }

  public static @Nullable String getDeclaredStringAttributeValue(@NotNull PsiAnnotation anno, final @Nullable String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findDeclaredAttributeValue(attributeName);
    return attrValue == null ? null : getStringAttributeValue(attrValue);
  }

  public static @Nullable String getStringAttributeValue(@NotNull PsiAnnotationMemberValue attrValue) {
    PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(attrValue.getProject()).getConstantEvaluationHelper();
    Object constValue = evaluationHelper.computeConstantExpression(attrValue);
    return constValue instanceof String ? (String)constValue : null;
  }

  public static @Nullable <T extends Annotation> T findAnnotationInHierarchy(@NotNull PsiModifierListOwner listOwner, @NotNull Class<T> annotationClass) {
    PsiAnnotation annotation = findAnnotationInHierarchy(listOwner, Collections.singleton(annotationClass.getName()));
    if (annotation == null) return null;
    AnnotationInvocationHandler handler = new AnnotationInvocationHandler(annotationClass, annotation);
    return ReflectionUtil.proxy(annotationClass, handler);
  }

  /**
   * Get an attribute as an instance of {@link PsiNameValuePair} by its name from the annotation
   * @param annotation annotation to look for the attribute
   * @param attributeName attribute name
   * @return an attribute as an instance of {@link PsiNameValuePair} or null
   */
  @Contract(pure = true)
  public static @Nullable PsiNameValuePair findDeclaredAttribute(@NotNull PsiAnnotation annotation, @Nullable("null means 'value'") @NonNls String attributeName) {
    if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attributeName)) attributeName = null;
    for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
      final String name = attribute.getName();
      if (Objects.equals(name, attributeName) || attributeName == null && PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
        return attribute;
      }
    }
    return null;
  }

  public static boolean equal(@Nullable PsiAnnotation a, @Nullable PsiAnnotation b) {
    if (a == null) {
      return b == null;
    }
    if (b == null) {
      return false;
    }
    final String name = a.getQualifiedName();
    if (name == null || !name.equals(b.getQualifiedName())) {
      return false;
    }
    final Map<String, PsiAnnotationMemberValue> valueMap1 = new HashMap<>(2);
    final Map<String, PsiAnnotationMemberValue> valueMap2 = new HashMap<>(2);
    if (!fillValueMap(a.getParameterList(), valueMap1) || !fillValueMap(b.getParameterList(), valueMap2) ||
        valueMap1.size() != valueMap2.size()) {
      return false;
    }
    for (Map.Entry<String, PsiAnnotationMemberValue> entry : valueMap1.entrySet()) {
      if (!equal(entry.getValue(), valueMap2.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private static boolean fillValueMap(@NotNull PsiAnnotationParameterList parameterList, @NotNull Map<? super String, ? super PsiAnnotationMemberValue> valueMap) {
    final PsiNameValuePair[] attributes1 = parameterList.getAttributes();
    for (PsiNameValuePair attribute : attributes1) {
      final PsiReference reference = attribute.getReference();
      if (reference == null) {
        return false;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiAnnotationMethod)) {
        return false;
      }
      final PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod)target;
      final PsiAnnotationMemberValue defaultValue = annotationMethod.getDefaultValue();
      final PsiAnnotationMemberValue value = attribute.getValue();
      if (equal(value, defaultValue)) {
        continue;
      }
      final String name1 = attribute.getName();
      valueMap.put(name1 == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name1, value);
    }
    return true;
  }

  public static boolean equal(@Nullable PsiAnnotationMemberValue value1, @Nullable PsiAnnotationMemberValue value2) {
    if (value1 instanceof PsiArrayInitializerMemberValue && value2 instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers1 = ((PsiArrayInitializerMemberValue)value1).getInitializers();
      final PsiAnnotationMemberValue[] initializers2 = ((PsiArrayInitializerMemberValue)value2).getInitializers();
      if (initializers1.length != initializers2.length) {
        return false;
      }
      return ArrayUtil.areEqual(initializers1, initializers2, AnnotationUtil::equal);
    }
    if (value1 != null && value2 != null) {
      final PsiConstantEvaluationHelper constantEvaluationHelper =
        JavaPsiFacade.getInstance(value1.getProject()).getConstantEvaluationHelper();
      final Object const1 = constantEvaluationHelper.computeConstantExpression(value1);
      final Object const2 = constantEvaluationHelper.computeConstantExpression(value2);
      return const1 != null && const1.equals(const2);
    }
    return false;
  }

  /**
   * Ignores Override and SuppressWarnings annotations.
   */
  public static boolean equal(PsiAnnotation @NotNull [] annotations1, PsiAnnotation @NotNull [] annotations2) {
    final Map<String, PsiAnnotation> map1 = buildAnnotationMap(annotations1);
    final Map<String, PsiAnnotation> map2 = buildAnnotationMap(annotations2);
    if (map1.size() != map2.size()) {
      return false;
    }
    for (Map.Entry<String, PsiAnnotation> entry : map1.entrySet()) {
      if (!equal(entry.getValue(), map2.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private static @NotNull @Unmodifiable Map<String, PsiAnnotation> buildAnnotationMap(PsiAnnotation @NotNull [] annotations) {
    final Map<String, PsiAnnotation> map = new HashMap<>();
    for (PsiAnnotation annotation : annotations) {
      map.put(annotation.getQualifiedName(), annotation);
    }
    map.remove(CommonClassNames.JAVA_LANG_OVERRIDE);
    map.remove("java.lang.SuppressWarnings");
    return map;
  }

  //<editor-fold desc="Deprecated stuff.">
  private static final String[] SIMPLE_NAMES =
    {"NotNull", "Nullable", "NonNls", "PropertyKey", "TestOnly", "Language", "Identifier", "Pattern", "PrintFormat", "RegExp", "Subst"};

  /** @deprecated simple name is not enough for reliable identification */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static boolean isJetbrainsAnnotation(@NotNull String simpleName) {
    return ArrayUtil.find(SIMPLE_NAMES, simpleName) != -1;
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, Collection, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations) {
    return isAnnotated(listOwner, annotations, CHECK_TYPE);
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, String, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN, boolean checkHierarchy) {
    return isAnnotated(listOwner, annotationFQN, flags(checkHierarchy, true, true));
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, String, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    @NotNull String annotationFQN,
                                    boolean checkHierarchy,
                                    boolean skipExternal) {
    return isAnnotated(listOwner, annotationFQN, flags(checkHierarchy, skipExternal, skipExternal));
  }

  @Flags
  private static int flags(boolean checkHierarchy, boolean skipExternal, boolean skipInferred) {
    int flags = CHECK_TYPE;
    if (checkHierarchy) flags |= CHECK_HIERARCHY;
    if (!skipExternal) flags |= CHECK_EXTERNAL;
    if (!skipInferred) flags |= CHECK_INFERRED;
    return flags;
  }
  //</editor-fold>

  public static @NotNull @Unmodifiable List<PsiAnnotationMemberValue> arrayAttributeValues(@Nullable PsiAnnotationMemberValue attributeValue) {
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      return Arrays.asList(((PsiArrayInitializerMemberValue)attributeValue).getInitializers());
    }
    return ContainerUtil.createMaybeSingletonList(attributeValue);
  }

  /**
   * @param annotation annotation
   * @return type that relates to that annotation
   */
  public static @Nullable PsiType getRelatedType(PsiAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (owner instanceof PsiType) {
      return (PsiType)owner;
    }
    PsiType type = null;
    if (owner instanceof PsiModifierList) {
      PsiElement parent = ((PsiModifierList)owner).getParent();
      PsiTypeElement typeElement = null;
      if (parent instanceof PsiVariable) {
        type = ((PsiVariable)parent).getType();
        typeElement = ((PsiVariable)parent).getTypeElement();
      }
      if (parent instanceof PsiMethod) {
        type = ((PsiMethod)parent).getReturnType();
        typeElement = ((PsiMethod)parent).getReturnTypeElement();
      }
      if (type != null) {
        PsiClass annoClass = annotation.resolveAnnotationType();
        if (annoClass != null) {
          Set<PsiAnnotation.TargetType> targets = AnnotationTargetUtil.getAnnotationTargets(annoClass);
          if (targets != null && targets.contains(PsiAnnotation.TargetType.TYPE_USE) &&
              PsiUtil.isAvailable(JavaFeature.TYPE_ANNOTATIONS, parent)) {
            if (typeElement != null && targets.size() == 1) {
              // For ambiguous annotations, we assume that annotation on the outer type relates to the inner type
              // E.g. @Nullable Outer.Inner is equivalent to Outer.@Nullable Inner (if @Nullable is not type-use only)
              PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
              // See com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl.getOwner
              while (ref != null && ref.isQualified()) {
                ref = ObjectUtils.tryCast(ref.getQualifier(), PsiJavaCodeReferenceElement.class);
              }
              if (ref != null) {
                return JavaPsiFacade.getElementFactory(annotation.getProject()).createType(ref);
              }
            }
            return type.getDeepComponentType();
          }
        }
        return type;
      }
    }
    return null;
  }
}