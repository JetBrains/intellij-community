// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Stream;

public class AnnotationUtil {
  public static final String NULLABLE = "org.jetbrains.annotations.Nullable";
  public static final String NOT_NULL = "org.jetbrains.annotations.NotNull";

  public static final String NON_NLS = "org.jetbrains.annotations.NonNls";
  public static final String NLS = "org.jetbrains.annotations.Nls";

  public static final String PROPERTY_KEY = "org.jetbrains.annotations.PropertyKey";
  public static final String PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER = "resourceBundle";

  public static final String TEST_ONLY = "org.jetbrains.annotations.TestOnly";

  public static final String LANGUAGE = "org.intellij.lang.annotations.Language";

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, String @NotNull ... annotationNames) {
    return findAnnotation(listOwner, false, annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, boolean skipExternal, String @NotNull ... annotationNames) {
    if (annotationNames.length == 0) return null;
    Set<String> set = annotationNames.length == 1 ? Collections.singleton(annotationNames[0]) : ContainerUtil.newHashSet(annotationNames);
    return findAnnotation(listOwner, set, skipExternal);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    return findAnnotation(listOwner, (Collection<String>)annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames) {
    return findAnnotation(listOwner, annotationNames, false);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames, boolean skipExternal) {
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
  @NotNull
  public static List<PsiAnnotation> findAllAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames,
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

  @Nullable
  private static List<PsiAnnotation> findOwnAnnotations(@NotNull final PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames) {
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

  @Nullable
  private static List<PsiAnnotation> findNonCodeAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames) {
    if (listOwner instanceof PsiLocalVariable) {
      // Non-code annotations for local variables are not supported: don't bother to search them
      return null;
    }
    Map<Collection<String>, List<PsiAnnotation>> map = CachedValuesManager.getCachedValue(
      listOwner,
      () -> {
        Map<Collection<String>, List<PsiAnnotation>> value = ConcurrentFactoryMap.createMap(
          annotationNames1 -> {
            PsiUtilCore.ensureValid(listOwner);
            final Project project = listOwner.getProject();
            List<PsiAnnotation> annotations = null;
            final ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);
            for (String annotationName : annotationNames1) {
              List<PsiAnnotation> externalAnnotations = externalAnnotationsManager.findExternalAnnotations(listOwner, annotationName);
              if (!externalAnnotations.isEmpty()) {
                if (annotations == null) {
                  annotations = new SmartList<>();
                }
                annotations.addAll(externalAnnotations);
              }
            }

            final InferredAnnotationsManager inferredAnnotationsManager = InferredAnnotationsManager.getInstance(project);
            for (String annotationName : annotationNames1) {
              final PsiAnnotation annotation = inferredAnnotationsManager.findInferredAnnotation(listOwner, annotationName);
              if (annotation != null) {
                if (annotations == null) {
                  annotations = new SmartList<>();
                }
                annotations.add(annotation);
              }
            }
            return annotations;
          }
        );
        return CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
      });
    return map.get(annotationNames);
  }

  public static PsiAnnotation @NotNull [] findAnnotations(@Nullable PsiModifierListOwner modifierListOwner, @NotNull Collection<String> annotationNames) {
    if (modifierListOwner == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    ArrayList<PsiAnnotation> result = null;
    for (final PsiAnnotation psiAnnotation : annotations) {
      if (annotationNames.contains(psiAnnotation.getQualifiedName())) {
        if (result == null) result = new ArrayList<>();
        result.add(psiAnnotation);
      }
    }
    return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @NotNull
  public static <T extends PsiModifierListOwner> List<T> getSuperAnnotationOwners(@NotNull T element) {
    return CachedValuesManager.getCachedValue(element, () -> {
      Set<PsiModifierListOwner> result = new LinkedHashSet<>();
      if (element instanceof PsiMethod) {
        if (!element.hasModifierProperty(PsiModifier.STATIC)) {
          collectSuperMethods(result, ((PsiMethod)element).getHierarchicalMethodSignature(), element,
                              JavaPsiFacade.getInstance(element.getProject()).getResolveHelper());
        }
      }
      else if (element instanceof PsiClass) {
        InheritanceUtil.processSupers((PsiClass)element, false, Processors.cancelableCollectProcessor(result));
      }
      else if (element instanceof PsiParameter) {
        collectSuperParameters(result, (PsiParameter)element);
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

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(@NotNull final PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    return findAnnotationInHierarchy(listOwner, annotationNames, false);
  }

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(@NotNull final PsiModifierListOwner listOwner,
                                                        @NotNull Set<String> annotationNames, boolean skipExternal) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames, skipExternal);
    if (directAnnotation != null) return directAnnotation;

    for (PsiModifierListOwner superOwner : getSuperAnnotationOwners(listOwner)) {
      PsiAnnotation annotation = findAnnotation(superOwner, annotationNames, skipExternal);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  private static void collectSuperParameters(@NotNull final Set<? super PsiModifierListOwner> result, @NotNull PsiParameter parameter) {
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

  /**
   * @param type type to check
   * @param qualifiedNames annotation qualified names of TYPE_USE annotations to look for
   * @return found type annotation, or null if not found. For type parameter types upper bound annotations are also checked
   */
  @Contract("null, _ -> null")
  public static @Nullable PsiAnnotation findAnnotationInTypeHierarchy(@Nullable PsiType type, @NotNull Set<String> qualifiedNames) {
    if (type == null) return null;
    Ref<PsiAnnotation> result = Ref.create(null);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      for (PsiAnnotation annotation : eachType.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedNames.contains(qualifiedName)) {
          result.set(annotation);
          return false;
        }
      }
      return !(eachType instanceof PsiClassType) || PsiUtil.resolveClassInClassTypeOnly(eachType) instanceof PsiTypeParameter;
    });
    return result.get();
  }

  @MagicConstant(flags = {CHECK_HIERARCHY, CHECK_EXTERNAL, CHECK_INFERRED, CHECK_TYPE})
  @Target({ElementType.PARAMETER, ElementType.METHOD})
  private @interface Flags { }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations, @Flags int flags) {
    return annotations.stream().anyMatch(annotation -> isAnnotated(listOwner, annotation, flags, null));
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
        if (processed == null) processed = new THashSet<>();
        if (!processed.add(method)) return false;
        for (PsiMethod superMethod : method.findSuperMethods()) {
          if (isAnnotated(superMethod, annotationFQN, flags, processed)) {
            return true;
          }
        }
      }
      else if (listOwner instanceof PsiClass) {
        PsiClass clazz = (PsiClass)listOwner;
        if (processed == null) processed = new THashSet<>();
        if (!processed.add(clazz)) return false;
        for (PsiClass superClass : clazz.getSupers()) {
          if (isAnnotated(superClass, annotationFQN, flags, processed)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt) {
    return isAnnotatingApplicable(elt, NullableNotNullManager.getInstance(elt.getProject()).getDefaultNullable());
  }

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt, @NotNull String annotationFQN) {
    final Project project = elt.getProject();
    return PsiUtil.isLanguageLevel5OrHigher(elt) &&
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
  public static boolean checkAnnotatedUsingPatterns(@Nullable PsiModifierListOwner owner, @NotNull Collection<String> annotations) {
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

  @Nullable
  public static PsiMethod getAnnotationMethod(@NotNull PsiNameValuePair pair) {
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
    if (externalAnnotations != null) {
      annotations = ArrayUtil.mergeArrays(annotations, externalAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }
    if (withInferred) {
      final PsiAnnotation[] inferredAnnotations = InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner);
      annotations = ArrayUtil.mergeArrays(annotations, inferredAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }

    if (inHierarchy) {
      if (owner instanceof PsiClass) {
        for (PsiClass superClass : ((PsiClass)owner).getSupers()) {
          if (visited == null) visited = new THashSet<>();
          if (visited.add(superClass)) annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superClass, true, visited, withInferred));
        }
      }
      else if (owner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)owner;
        PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

          final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
          PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(aClass.getProject());
          for (final HierarchicalMethodSignature superSignature : superSignatures) {
            final PsiMethod superMethod = superSignature.getMethod();
            if (visited == null) visited = new THashSet<>();
            if (!visited.add(superMethod)) continue;
            if (!resolveHelper.isAccessible(superMethod, owner, null)) continue;
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
            PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(aClass.getProject());
            for (final HierarchicalMethodSignature superSignature : superSignatures) {
              final PsiMethod superMethod = superSignature.getMethod();
              if (visited == null) visited = new THashSet<>();
              if (!visited.add(superMethod)) continue;
              if (!resolveHelper.isAccessible(superMethod, owner, null)) continue;
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

  @Nullable
  public static String getStringAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    return attrValue == null ? null : getStringAttributeValue(attrValue);
  }

  @Nullable
  public static Boolean getBooleanAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof Boolean ? (Boolean)constValue : null;
  }

  @Nullable
  public static Long getLongAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof Number ? ((Number)constValue).longValue() : null;
  }

  @Nullable
  public static String getDeclaredStringAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findDeclaredAttributeValue(attributeName);
    return attrValue == null ? null : getStringAttributeValue(attrValue);
  }

  @Nullable
  public static String getStringAttributeValue(@NotNull PsiAnnotationMemberValue attrValue) {
    PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(attrValue.getProject()).getConstantEvaluationHelper();
    Object constValue = evaluationHelper.computeConstantExpression(attrValue);
    return constValue instanceof String ? (String)constValue : null;
  }

  @Nullable
  public static <T extends Annotation> T findAnnotationInHierarchy(@NotNull PsiModifierListOwner listOwner, @NotNull Class<T> annotationClass) {
    PsiAnnotation annotation = findAnnotationInHierarchy(listOwner, Collections.singleton(annotationClass.getName()));
    if (annotation == null) return null;
    AnnotationInvocationHandler handler = new AnnotationInvocationHandler(annotationClass, annotation);
    @SuppressWarnings("unchecked") T t = (T)Proxy.newProxyInstance(annotationClass.getClassLoader(), new Class<?>[]{annotationClass}, handler);
    return t;
  }

  @Nullable
  public static PsiNameValuePair findDeclaredAttribute(@NotNull PsiAnnotation annotation, @Nullable("null means 'value'") String attributeName) {
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
    final Map<String, PsiAnnotationMemberValue> valueMap1 = new THashMap<>(2);
    final Map<String, PsiAnnotationMemberValue> valueMap2 = new THashMap<>(2);
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

  private static boolean fillValueMap(@NotNull PsiAnnotationParameterList parameterList, @NotNull Map<String, PsiAnnotationMemberValue> valueMap) {
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
      for (int i = 0; i < initializers1.length; i++) {
        if (!equal(initializers1[i], initializers2[i])) {
          return false;
        }
      }
      return true;
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

  @NotNull
  private static Map<String, PsiAnnotation> buildAnnotationMap(PsiAnnotation @NotNull [] annotations) {
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
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public static boolean isJetbrainsAnnotation(@NotNull String simpleName) {
    return ArrayUtil.find(SIMPLE_NAMES, simpleName) != -1;
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, Collection, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations) {
    return isAnnotated(listOwner, annotations, CHECK_TYPE);
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, Collection, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    @NotNull Collection<String> annotations,
                                    boolean checkHierarchy) {
    return isAnnotated(listOwner, annotations, flags(checkHierarchy, true, true));
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, String, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN, boolean checkHierarchy) {
    return isAnnotated(listOwner, annotationFQN, flags(checkHierarchy, true, true));
  }

  /** @deprecated use {@link #isAnnotated(PsiModifierListOwner, String, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
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

  @NotNull
  public static List<PsiAnnotationMemberValue> arrayAttributeValues(@Nullable PsiAnnotationMemberValue attributeValue) {
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      return Arrays.asList(((PsiArrayInitializerMemberValue)attributeValue).getInitializers());
    }
    return ContainerUtil.createMaybeSingletonList(attributeValue);
  }
}