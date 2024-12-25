/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Predicates.nonNull;

public final class ClassUtils {

  private static final Set<String> immutableTypes = new HashSet<>(19);

  private static final Set<PsiType> primitiveNumericTypes = new HashSet<>(7);

  private static final Set<PsiType> integralTypes = new HashSet<>(5);

  static {
    integralTypes.add(PsiTypes.longType());
    integralTypes.add(PsiTypes.intType());
    integralTypes.add(PsiTypes.shortType());
    integralTypes.add(PsiTypes.charType());
    integralTypes.add(PsiTypes.byteType());

    primitiveNumericTypes.add(PsiTypes.byteType());
    primitiveNumericTypes.add(PsiTypes.charType());
    primitiveNumericTypes.add(PsiTypes.shortType());
    primitiveNumericTypes.add(PsiTypes.intType());
    primitiveNumericTypes.add(PsiTypes.longType());
    primitiveNumericTypes.add(PsiTypes.floatType());
    primitiveNumericTypes.add(PsiTypes.doubleType());

    immutableTypes.add(CommonClassNames.JAVA_LANG_BOOLEAN);
    immutableTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
    immutableTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    immutableTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    immutableTypes.add(CommonClassNames.JAVA_LANG_LONG);
    immutableTypes.add(CommonClassNames.JAVA_LANG_FLOAT);
    immutableTypes.add(CommonClassNames.JAVA_LANG_DOUBLE);
    immutableTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    immutableTypes.add(CommonClassNames.JAVA_LANG_STRING);
    immutableTypes.add("java.awt.Font");
    immutableTypes.add("java.awt.BasicStroke");
    immutableTypes.add("java.awt.Color");
    immutableTypes.add("java.awt.Cursor");
    immutableTypes.add("java.math.BigDecimal");
    immutableTypes.add("java.math.BigInteger");
    immutableTypes.add("java.math.MathContext");
    immutableTypes.add("java.nio.channels.FileLock");
    immutableTypes.add("java.nio.charset.Charset");
    immutableTypes.add("java.io.File");
    immutableTypes.add("java.net.Inet4Address");
    immutableTypes.add("java.net.Inet6Address");
    immutableTypes.add("java.net.InetSocketAddress");
    immutableTypes.add("java.net.URI");
    immutableTypes.add("java.net.URL");
    immutableTypes.add("java.util.Locale");
    immutableTypes.add("java.util.UUID");
    immutableTypes.add("java.util.regex.Pattern");
    immutableTypes.add("java.time.ZoneOffset");
  }

  private ClassUtils() {}

  public static @Nullable PsiClass findClass(@NonNls String fqClassName, PsiElement context) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(fqClassName, context.getResolveScope());
  }

  public static @Nullable PsiClass findObjectClass(PsiElement context) {
    return findClass(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static boolean isPrimitive(PsiType type) {
    return TypeConversionUtil.isPrimitiveAndNotNull(type);
  }

  public static boolean isIntegral(PsiType type) {
    return integralTypes.contains(type);
  }

  /**
   * Checks whether given type represents a known immutable value (which visible state cannot be changed).
   * This call is equivalent to {@code isImmutable(type, true)}.
   * @param type type to check
   * @return true if type is known to be immutable; false otherwise
   */
  @Contract("null -> false")
  public static boolean isImmutable(@Nullable PsiType type) {
    return isImmutable(type, true);
  }

  /**
   * Checks whether given type represents a known immutable value (which visible state cannot be changed).
   *
   * @param type type to check
   * @param checkDocComment if true JavaDoc comment will be checked for {@code @Immutable} tag (which may cause AST loading).
   * @return true if type is known to be immutable; false otherwise
   */
  @Contract("null,_ -> false")
  public static boolean isImmutable(@Nullable PsiType type, boolean checkDocComment) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass == null) {
      return false;
    }
    return isImmutableClass(aClass, checkDocComment);
  }

  public static boolean isImmutableClass(@NotNull PsiClass aClass) {
    return isImmutableClass(aClass, false);
  }

  private static boolean isImmutableClass(@NotNull PsiClass aClass, boolean checkDocComment) {
    if (aClass.isRecord()) {
      return true;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName != null &&
        (immutableTypes.contains(qualifiedName) ||
         (qualifiedName.startsWith("com.google.common.collect.Immutable") && !qualifiedName.endsWith("Builder")))) {
      return true;
    }
    if (JCiPUtil.isImmutable(aClass, checkDocComment)) {
      return true;
    }

    return aClass.hasModifierProperty(PsiModifier.FINAL) &&
           ContainerUtil.and(aClass.getAllFields(),
                             field -> !field.hasModifierProperty(PsiModifier.STATIC) &&
                                      field.hasModifierProperty(PsiModifier.FINAL) &&
                                      (TypeConversionUtil.isPrimitiveAndNotNull(field.getType()) ||
                                       immutableTypes.contains(field.getType().getCanonicalText())));
  }

  public static boolean inSamePackage(@Nullable PsiElement element1, @Nullable PsiElement element2) {
    if (element1 == null || element2 == null) {
      return false;
    }
    final PsiFile containingFile1 = element1.getContainingFile();
    if (!(containingFile1 instanceof PsiClassOwner containingJavaFile1)) {
      return false;
    }
    final String packageName1 = containingJavaFile1.getPackageName();
    final PsiFile containingFile2 = element2.getContainingFile();
    if (!(containingFile2 instanceof PsiClassOwner containingJavaFile2)) {
      return false;
    }
    final String packageName2 = containingJavaFile2.getPackageName();
    return packageName1.equals(packageName2);
  }

  @Contract("_, null -> false")
  public static boolean isInsideClassBody(@NotNull PsiElement element, @Nullable PsiClass outerClass) {
    if (outerClass == null) {
      return false;
    }
    if (outerClass.isRecord() && PsiTreeUtil.isAncestor(outerClass.getRecordHeader(), element, true)) {
      return true;
    }
    final PsiElement brace = outerClass.getLBrace();
    return brace != null && brace.getTextOffset() < element.getTextOffset();
  }

  public static boolean isFieldVisible(@NotNull PsiField field, PsiClass fromClass) {
    final PsiClass fieldClass = field.getContainingClass();
    if (fieldClass == null) {
      return false;
    }
    if (fieldClass.equals(fromClass)) {
      return true;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    if (field.hasModifierProperty(PsiModifier.PUBLIC) ||
        field.hasModifierProperty(PsiModifier.PROTECTED)) {
      return true;
    }
    return inSamePackage(fieldClass, fromClass);
  }

  @Contract("null -> false")
  public static boolean isPrimitiveNumericType(@Nullable PsiType type) {
    return primitiveNumericTypes.contains(type);
  }

  public static boolean isInnerClass(PsiClass aClass) {
    final PsiClass parentClass = PsiUtil.getContainingClass(aClass);
    return parentClass != null;
  }

  /**
   * @return containing class for {@code element} ignoring {@link PsiAnonymousClass} if {@code element} is located in corresponding expression list
   * @deprecated use {@link PsiUtil#getContainingClass(PsiElement)}
   */
  @Deprecated
  public static @Nullable PsiClass getContainingClass(PsiElement element) {
    return PsiUtil.getContainingClass(element);
  }

  public static PsiClass getOutermostContainingClass(PsiClass aClass) {
    PsiClass outerClass = aClass;
    while (true) {
      final PsiClass containingClass = PsiUtil.getContainingClass(outerClass);
      if (containingClass != null) {
        outerClass = containingClass;
      }
      else {
        return outerClass;
      }
    }
  }

  public static @Nullable PsiClass getContainingStaticClass(PsiElement element) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false, PsiFile.class);
    while (isNonStaticClass(aClass)) {
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true, PsiFile.class);
    }
    return aClass;
  }

  public static boolean isNonStaticClass(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC) || aClass.isInterface() || aClass.isEnum()) {
      return false;
    }
    if (aClass instanceof PsiAnonymousClass) {
      return true;
    }
    final PsiElement parent = aClass.getParent();
    if (parent == null || parent instanceof PsiFile) {
      return false;
    }
    if (!(parent instanceof PsiClass parentClass)) {
      return true;
    }
    return !parentClass.isInterface();
  }

  /**
   * Returns "double brace" initialization for given anonymous class.
   *
   * @param aClass anonymous class to extract the "double brace" initializer from
   * @return "double brace" initializer or null if the class does not follow double brace initialization anti-pattern
   */
  public static @Nullable PsiClassInitializer getDoubleBraceInitializer(PsiAnonymousClass aClass) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length != 1) {
      return null;
    }
    final PsiClassInitializer initializer = initializers[0];
    if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }
    if (aClass.getFields().length != 0 || aClass.getMethods().length != 0 || aClass.getInnerClasses().length != 0) {
      return null;
    }
    if (aClass.getBaseClassReference().resolve() == null) {
      return null;
    }
    return initializer;
  }

  public static boolean isFinalClassWithDefaultEquals(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    if (!aClass.hasModifierProperty(PsiModifier.FINAL) && !hasOnlyPrivateConstructors(aClass)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("equals", true);
    for (PsiMethod method : methods) {
      if (!MethodUtils.isEquals(method)) {
        continue;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasOnlyPrivateConstructors(PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return false;
    }
    for (PsiMethod constructor : constructors) {
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isSingleton(@Nullable PsiClass aClass) {
    if (aClass == null || aClass.isInterface() || aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
      return false;
    }
    if (aClass.isEnum()) {
      if (!ControlFlowUtils.hasChildrenOfTypeCount(aClass, 1, PsiEnumConstant.class)) {
        return false;
      }
      // has at least on accessible instance method
      return ContainerUtil.exists(aClass.getMethods(), m -> !m.isConstructor() &&
                                                            !m.hasModifierProperty(PsiModifier.PRIVATE) &&
                                                            !m.hasModifierProperty(PsiModifier.STATIC));
    }
    final PsiMethod[] constructors = getIfOnlyInvisibleConstructors(aClass);
    if (constructors.length != 1) {
      return false;
    }
    final PsiField selfInstance = getIfOneStaticSelfInstance(aClass);
    return selfInstance != null && newOnlyAssignsToStaticSelfInstance(constructors[0], selfInstance);
  }

  private static PsiField getIfOneStaticSelfInstance(PsiClass aClass) {
    Stream<PsiField> fieldStream = Arrays.stream(aClass.getFields());

    StreamEx<PsiField> enclosingClassFields =
      StreamEx.iterate(aClass.getContainingClass(), nonNull(), c -> c.getContainingClass()).filter(nonNull())
              .flatMap(c -> Stream.of(c.getFields()));
    fieldStream = Stream.concat(fieldStream, enclosingClassFields);

    fieldStream = Stream.concat(fieldStream,
                                Arrays.stream(aClass.getInnerClasses())
                                      .filter(innerClass -> innerClass.hasModifierProperty(PsiModifier.STATIC))
                                      .flatMap(innerClass -> Arrays.stream(innerClass.getFields())));

    final List<PsiField> fields = fieldStream.filter(field -> resolveToSingletonField(aClass, field)).limit(2).toList();
    return fields.size() == 1 ? fields.get(0) : null;
  }

  private static boolean resolveToSingletonField(PsiClass aClass, PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(field.getType());
    PsiElement toCmp1 = aClass.isPhysical() ? aClass : aClass.getNavigationElement();
    PsiElement toCmp2 = targetClass == null || targetClass.isPhysical() ? targetClass : targetClass.getNavigationElement();
    return Objects.equals(toCmp1, toCmp2);
  }

  private static PsiMethod @NotNull [] getIfOnlyInvisibleConstructors(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return PsiMethod.EMPTY_ARRAY;
    }
    for (final PsiMethod constructor : constructors) {
      if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return PsiMethod.EMPTY_ARRAY;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          !constructor.hasModifierProperty(PsiModifier.PROTECTED)) {
        return PsiMethod.EMPTY_ARRAY;
      }
    }
    return constructors;
  }

  private static boolean newOnlyAssignsToStaticSelfInstance(PsiMethod method, final PsiField field) {
    if (field instanceof LightElement) return true;
    final Query<PsiReference> search = MethodReferencesSearch.search(method, method.getUseScope(), false);
    final NewOnlyAssignedToFieldProcessor processor = new NewOnlyAssignedToFieldProcessor(field);
    search.forEach(processor);
    return processor.isNewOnlyAssignedToField();
  }

  private static class NewOnlyAssignedToFieldProcessor implements Processor<PsiReference> {

    private boolean newOnlyAssignedToField = true;
    private final PsiField field;

    NewOnlyAssignedToFieldProcessor(PsiField field) {
      this.field = field;
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (field.equals(grandParent)) {
        return true;
      }
      if (!(grandParent instanceof PsiAssignmentExpression assignmentExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        newOnlyAssignedToField = false;
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!field.equals(target)) {
        newOnlyAssignedToField = false;
        return false;
      }
      return true;
    }

    public boolean isNewOnlyAssignedToField() {
      return newOnlyAssignedToField;
    }
  }

  /**
   * For use with property files.
   * <code>{0, choice, 1#class|2#interface|3#anonymous class derived from|4#annotation type|5#enum|6#record}</code>
   */
  public static int getTypeOrdinal(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return 3;
    if (aClass.isAnnotationType()) return 4;
    if (aClass.isInterface()) return 2;
    if (aClass.isEnum()) return 5;
    if (aClass.isRecord()) return 6;
    return 1;
  }
}