/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.magicConstant;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.slicer.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class MagicConstantInspection extends BaseJavaLocalInspectionTool {
  private static final Key<Boolean> NO_ANNOTATIONS_FOUND = Key.create("REPORTED_NO_ANNOTATIONS_FOUND");

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Magic Constant";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "MagicConstant";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        checkAnnotationsJarAttached(file, holder);
      }

      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r == null) return;
        PsiExpression l = expression.getLExpression();
        if (!(l instanceof PsiReferenceExpression)) return;
        PsiElement resolved = ((PsiReferenceExpression)l).resolve();
        if (!(resolved instanceof PsiModifierListOwner)) return;
        PsiModifierListOwner owner = (PsiModifierListOwner)resolved;
        PsiType type = expression.getType();
        checkExpression(r, owner, type, holder);
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) return;
        PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
        PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : LambdaUtil.getFunctionalInterfaceMethod(element);
        if (method == null) return;
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      public void visitNameValuePair(PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression)) return;
        PsiReference ref = pair.getReference();
        if (ref == null) return;
        PsiMethod method = (PsiMethod)ref.resolve();
        if (method == null) return;
        checkExpression((PsiExpression)value, method, method.getReturnType(), holder);
      }

      @Override
      public void visitBinaryExpression(PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) return;
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) return;
        checkBinary(l, r);
        checkBinary(r, l);
      }

      private void checkBinary(@NotNull PsiExpression l, @NotNull PsiExpression r) {
        if (l instanceof PsiReference) {
          PsiElement resolved = ((PsiReference)l).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            checkExpression(r, (PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), holder);
          }
        }
        else if (l instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)l).resolveMethod();
          if (method != null) {
            checkExpression(r, method, method.getReturnType(), holder);
          }
        }
      }
    };
  }

  @Override
  public void cleanup(@NotNull Project project) {
    super.cleanup(project);
    project.putUserData(NO_ANNOTATIONS_FOUND, null);
  }

  private static void checkAnnotationsJarAttached(@NotNull PsiFile file, @NotNull ProblemsHolder holder) {
    final Project project = file.getProject();
    if (!holder.isOnTheFly()) {
      final Boolean found = project.getUserData(NO_ANNOTATIONS_FOUND);
      if (found != null) return;
    }

    PsiClass event = JavaPsiFacade.getInstance(project).findClass("java.awt.event.InputEvent", GlobalSearchScope.allScope(project));
    if (event == null) return; // no jdk to attach
    PsiMethod[] methods = event.findMethodsByName("getModifiers", false);
    if (methods.length != 1) return; // no jdk to attach
    PsiMethod getModifiers = methods[0];
    PsiAnnotation annotation = ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(getModifiers, MagicConstant.class.getName());
    if (annotation != null) return;
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(getModifiers);
    if (virtualFile == null) return; // no jdk to attach
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
    Sdk jdk = null;
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof JdkOrderEntry) {
        jdk = ((JdkOrderEntry)orderEntry).getJdk();
        if (jdk != null) break;
      }
    }
    if (jdk == null) return; // no jdk to attach

    if (!holder.isOnTheFly()) {
      project.putUserData(NO_ANNOTATIONS_FOUND, Boolean.TRUE);
    }

    final Sdk finalJdk = jdk;

    String path = finalJdk.getHomePath();
    String text = "No IDEA annotations attached to the JDK " + finalJdk.getName() + (path == null ? "" : " (" + FileUtil.toSystemDependentName(path) + ")")
                  +", some issues will not be found";
    holder.registerProblem(file, text, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new LocalQuickFix() {
      @NotNull
      @Override
      public String getFamilyName() {
        return "Attach annotations";
      }

      @Nullable
      @Override
      public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
        return null;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        SdkModificator modificator = finalJdk.getSdkModificator();
        JavaSdkImpl.attachJdkAnnotations(modificator);
        modificator.commitChanges();
      }
    });
  }

  private static void checkExpression(@NotNull PsiExpression expression,
                                      @NotNull PsiModifierListOwner owner,
                                      @Nullable PsiType type,
                                      @NotNull ProblemsHolder holder) {
    AllowedValues allowed = getAllowedValues(owner, type, null);
    if (allowed == null) return;
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) scope = expression;
    if (!isAllowed(scope, expression, allowed, expression.getManager(), null)) {
      registerProblem(expression, allowed, holder);
    }
  }

  private static void checkCall(@NotNull PsiCallExpression methodCall, @NotNull ProblemsHolder holder) {
    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      AllowedValues values = getAllowedValues(parameter, parameter.getType(), null);
      if (values == null) continue;
      if (i >= arguments.length) break;
      PsiExpression argument = arguments[i];
      argument = PsiUtil.deparenthesizeExpression(argument);
      if (argument == null) continue;

      checkMagicParameterArgument(parameter, argument, values, holder);
    }
  }

  static class AllowedValues {
    @NotNull final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;
    final boolean resolvesToZero; //true if one if the values resolves to literal 0, e.g. "int PLAIN = 0"

    private AllowedValues(@NotNull PsiAnnotationMemberValue[] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
      resolvesToZero = resolvesToZero();
    }

    private boolean resolvesToZero() {
      for (PsiAnnotationMemberValue value : values) {
        if (value instanceof PsiExpression) {
          Object evaluated = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)value, null, false);
          if (evaluated instanceof Integer && ((Integer)evaluated).intValue() == 0) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new THashSet<>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new THashSet<>(Arrays.asList(a2.values));
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }
    @Override
    public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    boolean isSubsetOf(@NotNull AllowedValues other, @NotNull PsiManager manager) {
      for (PsiAnnotationMemberValue value : values) {
        boolean found = false;
        for (PsiAnnotationMemberValue otherValue : other.values) {
          if (same(value, otherValue, manager)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    }
  }

  private static AllowedValues getAllowedValuesFromMagic(@NotNull PsiType type,
                                                         @NotNull PsiAnnotation magic,
                                                         @NotNull PsiManager manager) {
    PsiAnnotationMemberValue[] allowedValues = PsiAnnotationMemberValue.EMPTY_ARRAY;
    boolean values = false, flags = false;
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue("intValues");
      if (intValues instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)intValues).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
      if (!values) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue("flags");
        if (orValue instanceof PsiArrayInitializerMemberValue) {
          final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)orValue).getInitializers();
          if (initializers.length != 0) {
            allowedValues = initializers;
            flags = true;
          }
        }
      }
    }
    else if (type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())))) {
      PsiAnnotationMemberValue strValuesAttr = magic.findAttributeValue("stringValues");
      if (strValuesAttr instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)strValuesAttr).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
    }
    else {
      return null; //other types not supported
    }

    PsiAnnotationMemberValue[] valuesFromClass = readFromClass("valuesFromClass", magic, type, manager);
    if (valuesFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, valuesFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      values = true;
    }
    PsiAnnotationMemberValue[] flagsFromClass = readFromClass("flagsFromClass", magic, type, manager);
    if (flagsFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, flagsFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      flags = true;
    }
    if (allowedValues.length == 0) {
      return null;
    }
    if (values && flags) {
      throw new IncorrectOperationException(
        "Misconfiguration of @MagicConstant annotation: 'flags' and 'values' shouldn't be used at the same time");
    }
    return new AllowedValues(allowedValues, flags);
  }

  private static PsiAnnotationMemberValue[] readFromClass(@NonNls @NotNull String attributeName,
                                                          @NotNull PsiAnnotation magic,
                                                          @NotNull PsiType type,
                                                          @NotNull PsiManager manager) {
    PsiAnnotationMemberValue fromClassAttr = magic.findAttributeValue(attributeName);
    PsiType fromClassType = fromClassAttr instanceof PsiClassObjectAccessExpression ? ((PsiClassObjectAccessExpression)fromClassAttr).getOperand().getType() : null;
    PsiClass fromClass = fromClassType instanceof PsiClassType ? ((PsiClassType)fromClassType).resolve() : null;
    if (fromClass == null) return null;
    String fqn = fromClass.getQualifiedName();
    if (fqn == null) return null;
    List<PsiAnnotationMemberValue> constants = new ArrayList<>();
    for (PsiField field : fromClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.PUBLIC) || !field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiType fieldType = field.getType();
      if (!Comparing.equal(fieldType, type)) continue;
      PsiAssignmentExpression e = (PsiAssignmentExpression)JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText("x="+fqn + "." + field.getName(), field);
      PsiReferenceExpression refToField = (PsiReferenceExpression)e.getRExpression();
      constants.add(refToField);
    }
    if (constants.isEmpty()) return null;

    return constants.toArray(new PsiAnnotationMemberValue[constants.size()]);
  }

  @Nullable
  static AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element, @Nullable PsiType type, @Nullable Set<PsiClass> visited) {
    PsiManager manager = element.getManager();
    for (PsiAnnotation annotation : getAllAnnotations(element)) {
      if (type != null && MagicConstant.class.getName().equals(annotation.getQualifiedName())) {
        AllowedValues values = getAllowedValuesFromMagic(type, annotation, manager);
        if (values != null) return values;
      }

      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      PsiElement resolved = ref == null ? null : ref.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) continue;
      PsiClass aClass = (PsiClass)resolved;

      if (visited == null) visited = new THashSet<>();
      if (!visited.add(aClass)) continue;
      AllowedValues values = getAllowedValues(aClass, type, visited);
      if (values != null) return values;
    }

    return parseBeanInfo(element, manager);
  }

  @NotNull
  private static PsiAnnotation[] getAllAnnotations(@NotNull PsiModifierListOwner element) {
    return CachedValuesManager.getCachedValue(element, () ->
      CachedValueProvider.Result.create(AnnotationUtil.getAllAnnotations(element, true, null, false),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static AllowedValues parseBeanInfo(@NotNull PsiModifierListOwner owner, @NotNull PsiManager manager) {
    PsiFile containingFile = owner.getContainingFile();
    if (containingFile != null && !containsBeanInfoText((PsiFile)containingFile.getNavigationElement())) {
      return null;
    }
    PsiMethod method = null;
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)owner;
      PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) return null;
      PsiElement nav = scope.getNavigationElement();
      if (!(nav instanceof PsiMethod)) return null;
      method = (PsiMethod)nav;
      if (method.isConstructor()) {
        // not a property, try the @ConstructorProperties({"prop"})
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "java.beans.ConstructorProperties");
        if (annotation == null) return null;
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (!(value instanceof PsiArrayInitializerMemberValue)) return null;
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        PsiElement parent = parameter.getParent();
        if (!(parent instanceof PsiParameterList)) return null;
        int index = ((PsiParameterList)parent).getParameterIndex(parameter);
        if (index >= initializers.length) return null;
        PsiAnnotationMemberValue initializer = initializers[index];
        if (!(initializer instanceof PsiLiteralExpression)) return null;
        Object val = ((PsiLiteralExpression)initializer).getValue();
        if (!(val instanceof String)) return null;
        PsiMethod setter = PropertyUtilBase.findPropertySetter(method.getContainingClass(), (String)val, false, false);
        if (setter == null) return null;
        // try the @beaninfo of the corresponding setter
        PsiElement navigationElement = setter.getNavigationElement();
        if (!(navigationElement instanceof PsiMethod)) return null;
        method = (PsiMethod)navigationElement;
      }
    }
    else if (owner instanceof PsiMethod) {
      PsiElement nav = owner.getNavigationElement();
      if (!(nav instanceof PsiMethod)) return null;
      method = (PsiMethod)nav;
    }
    if (method == null) return null;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      List<PsiMethod> setters = PropertyUtilBase.getSetters(aClass, PropertyUtilBase.getPropertyNameByGetter(method));
      if (setters.size() != 1) return null;
      method = setters.get(0);
    }
    if (!PropertyUtilBase.isSimplePropertySetter(method)) return null;
    PsiDocComment doc = method.getDocComment();
    if (doc == null) return null;
    PsiDocTag beaninfo = doc.findTagByName("beaninfo");
    if (beaninfo == null) return null;
    String data = StringUtil.join(beaninfo.getDataElements(), PsiElement::getText, "\n");
    int enumIndex = StringUtil.indexOfSubstringEnd(data, "enum:");
    if (enumIndex == -1) return null;
    data = data.substring(enumIndex);
    int colon = data.indexOf(':');
    int last = colon == -1 ? data.length() : data.substring(0,colon).lastIndexOf('\n');
    data = data.substring(0, last);

    List<PsiAnnotationMemberValue> values = new ArrayList<>();
    for (String line : StringUtil.splitByLines(data)) {
      List<String> words = StringUtil.split(line, " ", true, true);
      if (words.size() != 2) continue;
      String ref = words.get(1);
      PsiExpression constRef = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(ref, aClass);
      if (!(constRef instanceof PsiReferenceExpression)) continue;
      PsiReferenceExpression expr = (PsiReferenceExpression)constRef;
      values.add(expr);
    }
    if (values.isEmpty()) return null;
    PsiAnnotationMemberValue[] array = values.toArray(new PsiAnnotationMemberValue[values.size()]);
    return new AllowedValues(array, false);
  }

  private static boolean containsBeanInfoText(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(IdIndex.NAME,
                                                                                      new IdIndexEntry("beaninfo", true),
                                                                                      GlobalSearchScope.fileScope(file));
      return CachedValueProvider.Result.create(!files.isEmpty(), file);
    });
  }

  private static PsiType getType(@NotNull PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable)element).getType() : element instanceof PsiMethod ? ((PsiMethod)element).getReturnType() : null;
  }

  private static void checkMagicParameterArgument(@NotNull PsiParameter parameter,
                                                  @NotNull PsiExpression argument,
                                                  @NotNull AllowedValues allowedValues,
                                                  @NotNull ProblemsHolder holder) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() && !isAllowed(parameter.getDeclarationScope(), argument, allowedValues, manager, null)) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  private static void registerProblem(@NotNull PsiExpression argument, @NotNull AllowedValues allowedValues, @NotNull ProblemsHolder holder) {
    String values = StringUtil.join(allowedValues.values,
                                    value -> {
                                      if (value instanceof PsiReferenceExpression) {
                                        PsiElement resolved = ((PsiReferenceExpression)value).resolve();
                                        if (resolved instanceof PsiVariable) {
                                          return PsiFormatUtil.formatVariable((PsiVariable)resolved, PsiFormatUtilBase.SHOW_NAME |
                                                                                                     PsiFormatUtilBase.SHOW_CONTAINING_CLASS, PsiSubstitutor.EMPTY);
                                        }
                                      }
                                      return value.getText();
                                    }, ", ");
    String message = "Should be one of: " + values + (allowedValues.canBeOred ? " or their combination" : "");
    holder.registerProblem(argument, message, suggestMagicConstant(argument, allowedValues));
  }

  @Nullable // null means no quickfix available
  private static LocalQuickFix suggestMagicConstant(@NotNull PsiExpression argument,
                                                    @NotNull AllowedValues allowedValues) {
    Object argumentValue = JavaConstantExpressionEvaluator.computeConstantExpression(argument, null, false);
    if (argumentValue == null) return null;

    if (!allowedValues.canBeOred) {
      for (PsiAnnotationMemberValue value : allowedValues.values) {
        if (value instanceof PsiExpression) {
          Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)value, null, false);
          if (constantValue != null && constantValue.equals(argumentValue)) {
            return new ReplaceWithMagicConstantFix(argument, value);
          }
        }
      }
    }
    else {
      Long longArgument = evaluateLongConstant(argument);
      if (longArgument == null) { return null; }

      // try to find ored flags
      long remainingFlags = longArgument.longValue();
      List<PsiAnnotationMemberValue> flags = new ArrayList<>();
      for (PsiAnnotationMemberValue value : allowedValues.values) {
        if (value instanceof PsiExpression) {
          Long constantValue = evaluateLongConstant((PsiExpression)value);
          if (constantValue == null) {
            continue;
          }
          if ((remainingFlags & constantValue) == constantValue) {
            flags.add(value);
            remainingFlags &= ~constantValue;
          }
        }
      }
      if (remainingFlags == 0) {
        // found flags to combine with OR, suggest the fix
        if (flags.size() > 1) {
          for (int i = flags.size() - 1; i >= 0; i--) {
            PsiAnnotationMemberValue flag = flags.get(i);
            Long flagValue = evaluateLongConstant((PsiExpression)flag);
            if (flagValue != null && flagValue == 0) {
              // no sense in ORing with '0'
              flags.remove(i);
            }
          }
        }
        if (!flags.isEmpty()) {
          return new ReplaceWithMagicConstantFix(argument, flags.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY));
        }
      }
    }
    return null;
  }

  private static Long evaluateLongConstant(@NotNull PsiExpression expression) {
    Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
    if (constantValue instanceof Long ||
                 constantValue instanceof Integer ||
                 constantValue instanceof Short ||
                 constantValue instanceof Byte) {
      return ((Number)constantValue).longValue();
    }
    return null;
  }

  private static boolean isAllowed(@NotNull final PsiElement scope,
                                   @NotNull final PsiExpression argument,
                                   @NotNull final AllowedValues allowedValues,
                                   @NotNull final PsiManager manager,
                                   @Nullable Set<PsiExpression> visited) {
    if (isGoodExpression(argument, allowedValues, scope, manager, visited)) return true;

    return processValuesFlownTo(argument, scope, manager,
                                expression -> isGoodExpression(expression, allowedValues, scope, manager, visited));
  }

  private static boolean isGoodExpression(@NotNull PsiExpression argument,
                                          @NotNull AllowedValues allowedValues,
                                          @NotNull PsiElement scope,
                                          @NotNull PsiManager manager,
                                          @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(argument);
    if (expression == null) return true;
    if (visited == null) visited = new THashSet<>();
    if (!visited.add(expression)) return true;
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited);
      if (!thenAllowed) return false;
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited);
    }

    if (isOneOf(expression, allowedValues, manager)) return true;

    if (allowedValues.canBeOred) {
      PsiExpression zero = getLiteralExpression(expression, manager, "0");
      if (same(expression, zero, manager)
          // if for some crazy reason the constant with value "0" is included to allowed values for flags, do not treat literal "0" as allowed value anymore
          // see e.g. Font.BOLD=1, Font.ITALIC=2, Font.PLAIN=0
          && !allowedValues.resolvesToZero) return true;
      PsiExpression minusOne = getLiteralExpression(expression, manager, "-1");
      if (same(expression, minusOne, manager)) return true;
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
            if (!isAllowed(scope, operand, allowedValues, manager, visited)) return false;
          }
          return true;
        }
      }
      if (expression instanceof PsiPrefixExpression &&
          JavaTokenType.TILDE.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        return operand == null || isAllowed(scope, operand, allowedValues, manager, visited);
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    AllowedValues allowedForRef;
    if (resolved instanceof PsiModifierListOwner &&
        (allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), null)) != null &&
        allowedForRef.isSubsetOf(allowedValues, manager)) return true;

    return PsiType.NULL.equals(expression.getType());
  }

  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("LITERAL_EXPRESSION_CACHE");
  @NotNull
  private static PsiExpression getLiteralExpression(@NotNull PsiExpression context, @NotNull PsiManager manager, @NotNull String text) {
    Map<String, PsiExpression> cache = LITERAL_EXPRESSION_CACHE.get(manager);
    if (cache == null) {
      cache = ContainerUtil.createConcurrentSoftValueMap();
      cache = manager.putUserDataIfAbsent(LITERAL_EXPRESSION_CACHE, cache);
    }
    PsiExpression expression = cache.get(text);
    if (expression == null) {
      expression = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(text, context);
      cache.put(text, expression);
    }
    return expression;
  }

  private static boolean isOneOf(@NotNull PsiExpression expression, @NotNull AllowedValues allowedValues, @NotNull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.values) {
      if (same(allowedValue, expression, manager)) return true;
    }
    return false;
  }

  private static boolean same(@NotNull PsiElement e1, @NotNull PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Comparing.equal(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
    }
    if (e1 instanceof PsiPrefixExpression && e2 instanceof PsiPrefixExpression && ((PsiPrefixExpression)e1).getOperationTokenType() == ((PsiPrefixExpression)e2).getOperationTokenType()) {
      PsiExpression loperand = ((PsiPrefixExpression)e1).getOperand();
      PsiExpression roperand = ((PsiPrefixExpression)e2).getOperand();
      return loperand != null && roperand != null && same(loperand, roperand, manager);
    }
    if (e1 instanceof PsiReference && e2 instanceof PsiReference) {
      e1 = ((PsiReference)e1).resolve();
      e2 = ((PsiReference)e2).resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  private static boolean processValuesFlownTo(@NotNull final PsiExpression argument,
                                              @NotNull PsiElement scope,
                                              @NotNull PsiManager manager,
                                              @NotNull final Processor<PsiExpression> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(), LanguageSlicing.getProvider(argument).createRootUsage(argument, params));

    Collection<? extends AbstractTreeNode> children = rootNode.getChildren().iterator().next().getChildren();
    for (AbstractTreeNode child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      PsiElement element = usage.getElement();
      if (element instanceof PsiExpression && !processor.process((PsiExpression)element)) return false;
    }

    return !children.isEmpty();
  }

  private static class ReplaceWithMagicConstantFix extends LocalQuickFixOnPsiElement {
    private final List<SmartPsiElementPointer<PsiAnnotationMemberValue>> myMemberValuePointers;

    ReplaceWithMagicConstantFix(@NotNull PsiExpression argument, @NotNull PsiAnnotationMemberValue... values) {
      super(argument);
      myMemberValuePointers = Arrays.stream(values).map(
        value -> SmartPointerManager.getInstance(argument.getProject()).createSmartPsiElementPointer(value)).collect(Collectors.toList());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with magic constant";
    }

    @NotNull
    @Override
    public String getText() {
      List<String> names = myMemberValuePointers.stream().map(SmartPsiElementPointer::getElement).map(PsiElement::getText).collect(Collectors.toList());
      String expression = StringUtil.join(names, " | ");
      return "Replace with '" + expression + "'";
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      List<PsiAnnotationMemberValue> values = myMemberValuePointers.stream().map(SmartPsiElementPointer::getElement).collect(Collectors.toList());
      String text = StringUtil.join(Collections.nCopies(values.size(), "0"), " | ");
      PsiExpression concatExp = PsiElementFactory.SERVICE.getInstance(project).createExpressionFromText(text, startElement);

      List<PsiLiteralExpression> expressionsToReplace = new ArrayList<>(values.size());
      concatExp.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
          super.visitLiteralExpression(expression);
          if (Integer.valueOf(0).equals(expression.getValue())) {
            expressionsToReplace.add(expression);
          }
        }
      });
      Iterator<PsiAnnotationMemberValue> iterator = values.iterator();
      List<PsiElement> resolved = new ArrayList<>();
      for (PsiLiteralExpression toReplace : expressionsToReplace) {
        PsiAnnotationMemberValue value = iterator.next();
        resolved.add(((PsiReference)value).resolve());
        PsiExpression replaced = (PsiExpression)toReplace.replace(value);
        if (toReplace == concatExp) {
          concatExp = replaced;
        }
      }
      PsiElement newStartElement = startElement.replace(concatExp);
      Iterator<PsiElement> resolvedValuesIterator = resolved.iterator();
      newStartElement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          PsiElement bound = expression.bindToElement(resolvedValuesIterator.next());
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(bound);
        }
      });
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      boolean allValid = !myMemberValuePointers.stream().map(SmartPsiElementPointer::getElement).anyMatch(p -> p == null || !p.isValid());
      return allValid && super.isAvailable(project, file, startElement, endElement);
    }
  }
}
