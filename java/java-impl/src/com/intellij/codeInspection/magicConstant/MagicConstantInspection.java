/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.slicer.*;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MagicConstantInspection extends LocalInspectionTool {
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
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
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

      private void checkBinary(PsiExpression l, PsiExpression r) {
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

  private static void checkExpression(PsiExpression expression,
                                      PsiModifierListOwner owner,
                                      PsiType type,
                                      ProblemsHolder holder) {
    AllowedValues allowed = getAllowedValues(owner, type, null);
    if (allowed == null) return;
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) scope = expression;
    if (!isAllowed(scope, expression, allowed, expression.getManager())) {
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
      //PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, Collections.singletonList(MagicConstant.class.getName()));
      //if (annotation == null) continue;
      if (i >= arguments.length) break;
      PsiExpression argument = arguments[i];
      argument = PsiUtil.deparenthesizeExpression(argument);
      if (argument == null) continue;

      checkMagicParameterArgument(parameter, argument, values, holder);
    }
  }

  static class AllowedValues {
    final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;

    private AllowedValues(PsiAnnotationMemberValue[] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(a2.values));
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
      int result = values != null ? Arrays.hashCode(values) : 0;
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }
  }

  private static AllowedValues getAllowedValuesFromMagic(@NotNull PsiModifierListOwner element, @NotNull PsiType type) {
    PsiAnnotation magic = AnnotationUtil.findAnnotationInHierarchy(element, Collections.singleton(MagicConstant.class.getName()));
    if (magic == null) return null;
    PsiAnnotationMemberValue[] allowedValues;
    final boolean canBeOred;
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue("intValues");
      allowedValues = intValues instanceof PsiArrayInitializerMemberValue
        ? ((PsiArrayInitializerMemberValue)intValues).getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
      if (allowedValues.length == 0) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue("flags");
        allowedValues = orValue instanceof PsiArrayInitializerMemberValue ? ((PsiArrayInitializerMemberValue)orValue).getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
        canBeOred = true;
      }
      else {
        canBeOred = false;
      }
    }
    else if (type.equals(PsiType.getJavaLangString(element.getManager(), GlobalSearchScope.allScope(element.getProject())))) {
      PsiAnnotationMemberValue strValuesAttr = magic.findAttributeValue("stringValues");
      allowedValues = strValuesAttr instanceof PsiArrayInitializerMemberValue ? ((PsiArrayInitializerMemberValue)strValuesAttr).getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;
      canBeOred = false;
    }
    else {
      return null; //other types not supported
    }

    if (allowedValues.length != 0) {
      return new AllowedValues(allowedValues, canBeOred);
    }

    // last resort: try valuesFromClass
    PsiAnnotationMemberValue[] values = readFromClass("valuesFromClass", magic, type);
    boolean ored = false;
    if (values == null) {
      values = readFromClass("flagsFromClass", magic, type);
      ored = true;
    }
    if (values == null) return null;
    return new AllowedValues(values, ored);
  }

  private static PsiAnnotationMemberValue[] readFromClass(@NonNls String attributeName, @NotNull PsiAnnotation magic, PsiType type) {
    PsiAnnotationMemberValue fromClassAttr = magic.findAttributeValue(attributeName);
    PsiType fromClassType = fromClassAttr instanceof PsiClassObjectAccessExpression ? ((PsiClassObjectAccessExpression)fromClassAttr).getOperand().getType() : null;
    PsiClass fromClass = fromClassType instanceof PsiClassType ? ((PsiClassType)fromClassType).resolve() : null;
    if (fromClass == null) return null;
    String fqn = fromClass.getQualifiedName();
    if (fqn == null) return null;
    List<PsiAnnotationMemberValue> constants = new ArrayList<PsiAnnotationMemberValue>();
    for (PsiField field : fromClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.PUBLIC) || !field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiType fieldType = field.getType();
      if (!Comparing.equal(fieldType, type)) continue;
      PsiAssignmentExpression e = (PsiAssignmentExpression)JavaPsiFacade.getElementFactory(field.getProject()).createExpressionFromText("x="+fqn + "." + field.getName(), field);
      PsiReferenceExpression refToField = (PsiReferenceExpression)e.getRExpression();
      constants.add(refToField);
    }
    if (constants.isEmpty()) return null;

    return constants.toArray(new PsiAnnotationMemberValue[constants.size()]);
  }
  
  static AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element, PsiType type, Set<PsiClass> visited) {
    AllowedValues values;
    if (type != null) {
      values = getAllowedValuesFromMagic(element, type);
      if (values != null) return values;
    }

    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(element, true, null);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      PsiElement resolved = ref == null ? null : ref.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) continue;
      PsiClass aClass = (PsiClass)resolved;
      if (visited == null) visited = new THashSet<PsiClass>();
      if (!visited.add(aClass)) continue;
      values = getAllowedValues(aClass, type, visited);
      if (values != null) return values;
    }


    return parseBeanInfo(element);
  }

  private static AllowedValues parseBeanInfo(@NotNull PsiModifierListOwner owner) {
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
        PsiMethod setter = PropertyUtil.findPropertySetter(method.getContainingClass(), (String)val, false, false);
        if (setter == null) return null;
        // try the @beaninfo of the corresponding setter
        method = (PsiMethod)setter.getNavigationElement();
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
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      List<PsiMethod> setters = PropertyUtil.getSetters(aClass, PropertyUtil.getPropertyNameByGetter(method));
      if (setters.size() != 1) return null;
      method = setters.get(0);
    }
    if (!PropertyUtil.isSimplePropertySetter(method)) return null;
    PsiDocComment doc = method.getDocComment();
    if (doc == null) return null;
    PsiDocTag beaninfo = doc.findTagByName("beaninfo");
    if (beaninfo == null) return null;
    String data = StringUtil.join(beaninfo.getDataElements(), new Function<PsiElement, String>() {
      @Override
      public String fun(PsiElement element) {
        return element.getText();
      }
    }, "\n");
    int enumIndex = StringUtil.indexOfSubstringEnd(data, "enum:");
    if (enumIndex == -1) return null;
    data = data.substring(enumIndex);
    int colon = data.indexOf(":");
    int last = colon == -1 ? data.length() : data.substring(0,colon).lastIndexOf("\n");
    data = data.substring(0, last);

    List<PsiAnnotationMemberValue> values = new ArrayList<PsiAnnotationMemberValue>();
    for (String line : StringUtil.splitByLines(data)) {
      List<String> words = StringUtil.split(line, " ", true, true);
      if (words.size() != 2) continue;
      String ref = words.get(1);
      PsiExpression constRef = JavaPsiFacade.getElementFactory(aClass.getProject()).createExpressionFromText(ref, aClass);
      if (!(constRef instanceof PsiReferenceExpression)) continue;
      PsiReferenceExpression expr = (PsiReferenceExpression)constRef;
      values.add(expr);
    }
    if (values.isEmpty()) return null;
    PsiAnnotationMemberValue[] array = values.toArray(new PsiAnnotationMemberValue[values.size()]);
    return new AllowedValues(array, false);
  }

  private static PsiType getType(PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable)element).getType() : element instanceof PsiMethod ? ((PsiMethod)element).getReturnType() : null;
  }

  private static void checkMagicParameterArgument(@NotNull PsiParameter parameter,
                                                  PsiExpression argument,
                                                  @NotNull AllowedValues allowedValues,
                                                  @NotNull ProblemsHolder holder) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() && !isAllowed(parameter.getDeclarationScope(), argument, allowedValues, manager)) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  private static void registerProblem(PsiExpression argument, AllowedValues allowedValues, ProblemsHolder holder) {
    String values = StringUtil.join(allowedValues.values,
                                    new Function<PsiAnnotationMemberValue, String>() {
                                      @Override
                                      public String fun(PsiAnnotationMemberValue value) {
                                        if (value instanceof PsiReferenceExpression) {
                                          PsiElement resolved = ((PsiReferenceExpression)value).resolve();
                                          if (resolved instanceof PsiVariable) {
                                            return PsiFormatUtil.formatVariable((PsiVariable)resolved, PsiFormatUtilBase.SHOW_NAME |
                                                                                                       PsiFormatUtilBase.SHOW_CONTAINING_CLASS, PsiSubstitutor.EMPTY);
                                          }
                                        }
                                        return value.getText();
                                      }
                                    }, ", ");
    holder.registerProblem(argument, "Must be one of the: "+ values);
  }

  private static boolean isAllowed(@NotNull final PsiElement scope,
                                   @NotNull final PsiExpression argument,
                                   @NotNull final AllowedValues allowedValues,
                                   @NotNull final PsiManager manager) {
    if (isGoodExpression(argument, allowedValues, scope, manager)) return true;

    return processValuesFlownTo(argument, scope, new Processor<PsiExpression>() {
      @Override
      public boolean process(PsiExpression expression) {
        if (false & !PsiTreeUtil.isAncestor(scope, expression, false)) return true;
        return isGoodExpression(expression, allowedValues, scope, manager);
      }
    });
  }

  private static boolean isGoodExpression(PsiExpression expression,
                                          AllowedValues allowedValues,
                                          PsiElement scope,
                                          PsiManager manager) {
    expression = PsiUtil.deparenthesizeExpression(expression);
    if (expression == null) return true;
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager);
      if (!thenAllowed) return false;
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager);
    }

    if (isOneOf(expression, allowedValues, manager)) return true;

    if (allowedValues.canBeOred) {
      if (expression instanceof PsiPolyadicExpression &&
          JavaTokenType.OR.equals(((PsiPolyadicExpression)expression).getOperationTokenType())) {
        for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
          if (!isAllowed(scope, operand, allowedValues, manager)) return false;
        }
        return true;
      }
      //todo & ~(CONST | CONST)
      PsiExpression zero = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText("0", expression);
      if (same(expression, zero, manager)) return true;
      PsiExpression mOne = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText("-1", expression);
      if (same(expression, mOne, manager)) return true;
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
        Comparing.equal(allowedValues, allowedForRef)) return true;

    return PsiType.NULL.equals(expression.getType());
  }

  private static boolean isOneOf(@NotNull PsiExpression expression, @NotNull AllowedValues allowedValues, @NotNull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.values) {
      if (same(allowedValue, expression, manager)) return true;
    }
    return false;
  }

  private static boolean same(PsiElement e1, PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Comparing.equal(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
    }
    if (e1 instanceof PsiPrefixExpression && e2 instanceof PsiPrefixExpression && ((PsiPrefixExpression)e1).getOperationTokenType() == ((PsiPrefixExpression)e2).getOperationTokenType()) {
      return same(((PsiPrefixExpression)e1).getOperand(), ((PsiPrefixExpression)e2).getOperand(), manager);
    }
    if (e1 instanceof PsiReference && e2 instanceof PsiReference) {
      e1 = ((PsiReference)e1).resolve();
      e2 = ((PsiReference)e2).resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  private static boolean processValuesFlownTo(@NotNull final PsiExpression argument,
                                              PsiElement scope,
                                              @NotNull final Processor<PsiExpression> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), argument.getProject());

    SliceRootNode rootNode = new SliceRootNode(scope.getProject(), new DuplicateMap(), SliceManager.createRootUsage(argument, params));

    Collection<? extends AbstractTreeNode> children = rootNode.getChildren().iterator().next().getChildren();
    for (AbstractTreeNode child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      PsiElement element = usage.getElement();
      if (element instanceof PsiExpression && !processor.process((PsiExpression)element)) return false;
    }


    return !children.isEmpty();

    //Set<PsiElement> elements = new THashSet<PsiElement>(DfaUtil.getPossibleInitializationElements(argument));
    //elements.remove(argument);
    //if (elements.isEmpty()) return processor.process(argument);
    //for (PsiElement element : elements) {
    //  if (element != argument && !processor.process((PsiExpression)element)) return false;
    //}
    //return processor.process(argument);

  }

}




























