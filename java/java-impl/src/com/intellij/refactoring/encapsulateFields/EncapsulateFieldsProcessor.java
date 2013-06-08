
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.encapsulateFields;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor");

  private PsiClass myClass;
  @NotNull
  private final EncapsulateFieldsDescriptor myDescriptor;
  private final FieldDescriptor[] myFieldDescriptors;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project, @NotNull EncapsulateFieldsDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myFieldDescriptors = myDescriptor.getSelectedFields();
    myClass = myFieldDescriptors[0].getField().getContainingClass();
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    FieldDescriptor[] fields = new FieldDescriptor[myFieldDescriptors.length];
    System.arraycopy(myFieldDescriptors, 0, fields, 0, myFieldDescriptors.length);
    return new EncapsulateFieldsViewDescriptor(fields);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("encapsulate.fields.command.name", DescriptiveNameUtil.getDescriptiveName(myClass));
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    checkExistingMethods(conflicts, true);
    checkExistingMethods(conflicts, false);
    final Collection<PsiClass> classes = ClassInheritorsSearch.search(myClass).findAll();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final Set<PsiMethod> setters = new HashSet<PsiMethod>();
      final Set<PsiMethod> getters = new HashSet<PsiMethod>();

      for (PsiClass aClass : classes) {
        final PsiMethod getterOverrider =
          myDescriptor.isToEncapsulateGet() ? aClass.findMethodBySignature(fieldDescriptor.getGetterPrototype(), false) : null;
        if (getterOverrider != null) {
          getters.add(getterOverrider);
        }
        final PsiMethod setterOverrider =
          myDescriptor.isToEncapsulateSet() ? aClass.findMethodBySignature(fieldDescriptor.getSetterPrototype(), false) : null;
        if (setterOverrider != null) {
          setters.add(setterOverrider);
        }
      }
      if (!getters.isEmpty() || !setters.isEmpty()) {
        final PsiField field = fieldDescriptor.getField();
        for (PsiReference reference : ReferencesSearch.search(field)) {
          final PsiElement place = reference.getElement();
          LOG.assertTrue(place instanceof PsiReferenceExpression);
          final PsiExpression qualifierExpression = ((PsiReferenceExpression)place).getQualifierExpression();
          final PsiClass ancestor;
          if (qualifierExpression == null) {
            ancestor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
          }
          else {
            ancestor = PsiUtil.resolveClassInType(qualifierExpression.getType());
          }

          final boolean isGetter = !PsiUtil.isAccessedForWriting((PsiExpression)place);
          for (PsiMethod overridden : isGetter ? getters : setters) {
            if (InheritanceUtil.isInheritorOrSelf(myClass, ancestor, true)) {
              conflicts.putValue(overridden, "There is already a " +
                                             RefactoringUIUtil.getDescription(overridden, true) +
                                             " which would hide generated " +
                                             (isGetter ? "getter" : "setter") + " for " + place.getText());
              break;
            }
          }
        }
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  private void checkExistingMethods(MultiMap<PsiElement, String> conflicts, boolean isGetter) {
    if (isGetter) {
      if (!myDescriptor.isToEncapsulateGet()) return;
    }
    else {
      if (!myDescriptor.isToEncapsulateSet()) return;
    }

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      PsiMethod prototype = isGetter?descriptor.getGetterPrototype():descriptor.getSetterPrototype();
      final PsiType prototypeReturnType = prototype.getReturnType();
      PsiMethod existing = myClass.findMethodBySignature(prototype, true);
      if (existing != null) {
        final PsiType returnType = existing.getReturnType();
        if (!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
          final String descr = PsiFormatUtil.formatMethod(existing,
                                                          PsiSubstitutor.EMPTY,
                                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE,
                                                          PsiFormatUtilBase.SHOW_TYPE
          );
          String message = isGetter ?
                           RefactoringBundle.message("encapsulate.fields.getter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
                                                CommonRefactoringUtil.htmlEmphasize(prototype.getName())) :
                           RefactoringBundle.message("encapsulate.fields.setter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
                                                CommonRefactoringUtil.htmlEmphasize(prototype.getName()));
          conflicts.putValue(existing, message);
        }
      } else {
        PsiClass containingClass = myClass.getContainingClass();
        while (containingClass != null && existing == null) {
          existing = containingClass.findMethodBySignature(prototype, true);
          if (existing != null) {
            for (PsiReference reference : ReferencesSearch.search(existing)) {
              final PsiElement place = reference.getElement();
              LOG.assertTrue(place instanceof PsiReferenceExpression);
              final PsiExpression qualifierExpression = ((PsiReferenceExpression)place).getQualifierExpression();
              final PsiClass inheritor;
              if (qualifierExpression == null) {
                inheritor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
              } else {
                inheritor = PsiUtil.resolveClassInType(qualifierExpression.getType());
              }

              if (InheritanceUtil.isInheritorOrSelf(inheritor, myClass, true)) {
                conflicts.putValue(existing, "There is already a " + RefactoringUIUtil.getDescription(existing, true) + " which would be hidden by generated " + (isGetter ? "getter" : "setter"));
                break;
              }
            }
          }
          containingClass = containingClass.getContainingClass();
        }
      }
    }
  }

  @NotNull protected UsageInfo[] findUsages() {
    boolean findGet = myDescriptor.isToEncapsulateGet();
    boolean findSet = myDescriptor.isToEncapsulateSet();
    PsiModifierList newModifierList = null;
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    if (!myDescriptor.isToUseAccessorsWhenAccessible()){
      PsiElementFactory factory = facade.getElementFactory();
      try{
        PsiField field = factory.createField("a", PsiType.INT);
        setNewFieldVisibility(field);
        newModifierList = field.getModifierList();
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }

    ArrayList<EncapsulateFieldUsageInfo> array = ContainerUtil.newArrayList();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      for (final PsiReference reference : ReferencesSearch.search(fieldDescriptor.getField())) {
        if (!(reference instanceof PsiReferenceExpression)) continue;
        PsiReferenceExpression ref = (PsiReferenceExpression)reference;
        // [Jeka] to avoid recursion in the field's accessors
        if (findGet && isUsedInExistingAccessor(fieldDescriptor.getGetterPrototype(), ref)) continue;
        if (findSet && isUsedInExistingAccessor(fieldDescriptor.getSetterPrototype(), ref)) continue;
        if (!findGet) {
          if (!PsiUtil.isAccessedForWriting(ref)) continue;
        }
        if (!findSet || fieldDescriptor.getField().hasModifierProperty(PsiModifier.FINAL)) {
          if (!PsiUtil.isAccessedForReading(ref)) continue;
        }
        if (!myDescriptor.isToUseAccessorsWhenAccessible()) {
          PsiClass accessObjectClass = null;
          PsiExpression qualifier = ref.getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }
          final PsiResolveHelper helper = JavaPsiFacade.getInstance(((PsiReferenceExpression)reference).getProject()).getResolveHelper();
          if (helper.isAccessible(fieldDescriptor.getField(), newModifierList, ref, accessObjectClass, null)) {
            continue;
          }
        }
        EncapsulateFieldUsageInfo usageInfo = new EncapsulateFieldUsageInfo(ref, fieldDescriptor);
        array.add(usageInfo);
      }
    }
    EncapsulateFieldUsageInfo[] usageInfos = array.toArray(new EncapsulateFieldUsageInfo[array.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myFieldDescriptors.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFieldDescriptors[idx].refreshField((PsiField)element);
    }

    myClass = myFieldDescriptors[0].getField().getContainingClass();
  }

  protected void performRefactoring(UsageInfo[] usages) {
    updateFieldVisibility();
    generateAccessors();
    processUsagesPerFile(usages);
  }

  private void updateFieldVisibility() {
    if (myDescriptor.getFieldsVisibility() == null) return;

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      setNewFieldVisibility(descriptor.getField());
    }
  }

  private void generateAccessors() {
    // generate accessors
    myNameToGetter = new HashMap<String, PsiMethod>();
    myNameToSetter = new HashMap<String, PsiMethod>();

    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final DocCommentPolicy<PsiDocComment> commentPolicy = new DocCommentPolicy<PsiDocComment>(myDescriptor.getJavadocPolicy());

      PsiField field = fieldDescriptor.getField();
      final PsiDocComment docComment = field.getDocComment();
      if (myDescriptor.isToEncapsulateGet()) {
        final PsiMethod prototype = fieldDescriptor.getGetterPrototype();
        assert prototype != null;
        final PsiMethod getter = addOrChangeAccessor(prototype, myNameToGetter);
        if (docComment != null) {
          final PsiDocComment getterJavadoc = (PsiDocComment)getter.addBefore(docComment, getter.getFirstChild());
          commentPolicy.processNewJavaDoc(getterJavadoc);
        }
      }
      if (myDescriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL)) {
        PsiMethod prototype = fieldDescriptor.getSetterPrototype();
        assert prototype != null;
        addOrChangeAccessor(prototype, myNameToSetter);
      }

      if (docComment != null) {
        commentPolicy.processOldJavaDoc(docComment);
      }
    }
  }

  private void processUsagesPerFile(UsageInfo[] usages) {
    Map<PsiFile, List<EncapsulateFieldUsageInfo>> usagesInFiles = new HashMap<PsiFile, List<EncapsulateFieldUsageInfo>>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      final PsiFile file = element.getContainingFile();
      List<EncapsulateFieldUsageInfo> usagesInFile = usagesInFiles.get(file);
      if (usagesInFile == null) {
        usagesInFile = new ArrayList<EncapsulateFieldUsageInfo>();
        usagesInFiles.put(file, usagesInFile);
      }
      usagesInFile.add(((EncapsulateFieldUsageInfo)usage));
    }

    for (List<EncapsulateFieldUsageInfo> usageInfos : usagesInFiles.values()) {
      //this is to avoid elements to become invalid as a result of processUsage
      final EncapsulateFieldUsageInfo[] infos = usageInfos.toArray(new EncapsulateFieldUsageInfo[usageInfos.size()]);
      CommonRefactoringUtil.sortDepthFirstRightLeftOrder(infos);

      for (EncapsulateFieldUsageInfo info : infos) {
        processUsage(info);
      }
    }
  }

  private void setNewFieldVisibility(PsiField field) {
    try{
      if (myDescriptor.getFieldsVisibility() != null){
        field.normalizeDeclaration();
        PsiUtil.setModifierProperty(field, myDescriptor.getFieldsVisibility(), true);
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private PsiMethod addOrChangeAccessor(PsiMethod prototype, HashMap<String,PsiMethod> nameToAncestor) {
    PsiMethod existing = myClass.findMethodBySignature(prototype, false);
    PsiMethod result = existing;
    try{
      if (existing == null){
        PsiUtil.setModifierProperty(prototype, myDescriptor.getAccessorsVisibility(), true);
        result = (PsiMethod) myClass.add(prototype);
      }
      else{
        //TODO : change visibility
      }
      nameToAncestor.put(prototype.getName(), result);
      return result;
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return null;
  }

  private boolean isUsedInExistingAccessor(PsiMethod prototype, PsiElement element) {
    PsiMethod existingAccessor = myClass.findMethodBySignature(prototype, false);
    if (existingAccessor != null) {
      PsiElement parent = element;
      while (parent != null) {
        if (existingAccessor.equals(parent)) return true;
        parent = parent.getParent();
      }
    }
    return false;
  }

  private void processUsage(EncapsulateFieldUsageInfo usage) {
    final FieldDescriptor fieldDescriptor = usage.getFieldDescriptor();
    PsiField field = fieldDescriptor.getField();
    boolean processGet = myDescriptor.isToEncapsulateGet();
    boolean processSet = myDescriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

    try{
      final PsiReferenceExpression expr = (PsiReferenceExpression)usage.getElement();
      if (expr == null) return;
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression)parent).getLExpression())){
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() == null) return;
        PsiJavaToken opSign = assignment.getOperationSign();
        IElementType opType = opSign.getTokenType();
        if (opType == JavaTokenType.EQ) {
          {
            if (!processSet) return;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldDescriptor, setterArgument, expr);

            if (methodCall != null) {
              assignment.replace(methodCall);
            }
            //TODO: check if value is used!!!
          }
        }
        else if (opType == JavaTokenType.ASTERISKEQ || opType == JavaTokenType.DIVEQ || opType == JavaTokenType.PERCEQ ||
                 opType == JavaTokenType.PLUSEQ ||
                 opType == JavaTokenType.MINUSEQ ||
                 opType == JavaTokenType.LTLTEQ ||
                 opType == JavaTokenType.GTGTEQ ||
                 opType == JavaTokenType.GTGTGTEQ ||
                 opType == JavaTokenType.ANDEQ ||
                 opType == JavaTokenType.OREQ ||
                 opType == JavaTokenType.XOREQ) {
          {
            // Q: side effects of qualifier??!

            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            PsiExpression getExpr = expr;
            if (processGet) {
              final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            @NonNls String text = "a" + opName + "b";
            PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, expr);
            binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
            binExpr.getLOperand().replace(getExpr);
            binExpr.getROperand().replace(assignment.getRExpression());

            PsiExpression setExpr;
            if (processSet) {
              setExpr = createSetterCall(fieldDescriptor, binExpr, expr);
            }
            else {
              text = "a = b";
              PsiAssignmentExpression assignment1 = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
              assignment1 = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment1);
              assignment1.getLExpression().replace(expr);
              assignment1.getRExpression().replace(binExpr);
              setExpr = assignment1;
            }

            assignment.replace(setExpr);
            //TODO: check if value is used!!!
          }
        }
      }
      else if (RefactoringUtil.isPlusPlusOrMinusMinus(parent)){
        IElementType sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationTokenType();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationTokenType();
        }

        PsiExpression getExpr = expr;
        if (processGet){
          final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr);
          if(getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text;
        if (sign == JavaTokenType.PLUSPLUS){
          text = "a+1";
        }
        else{
          text = "a-1";
        }
        PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, null);
        binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
        binExpr.getLOperand().replace(getExpr);

        PsiExpression setExpr;
        if (processSet){
          setExpr = createSetterCall(fieldDescriptor, binExpr, expr);
        }
        else{
          text = "a = b";
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment);
          assignment.getLExpression().replace(expr);
          assignment.getRExpression().replace(binExpr);
          setExpr = assignment;
        }
        parent.replace(setExpr);
      }
      else{
        if (!processGet) return;
        PsiMethodCallExpression methodCall = createGetterCall(fieldDescriptor, expr);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private PsiMethodCallExpression createSetterCall(FieldDescriptor fieldDescriptor, final PsiExpression setterArgument, PsiReferenceExpression expr) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String setterName = fieldDescriptor.getSetterName();
    @NonNls String text = setterName + "(a)";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    methodCall.getArgumentList().getExpressions()[0].replace(setterArgument);
    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }
    final PsiMethod targetMethod = myNameToSetter.get(setterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private PsiMethodCallExpression createGetterCall(FieldDescriptor fieldDescriptor, PsiReferenceExpression expr)
          throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String getterName = fieldDescriptor.getGetterName();
    @NonNls String text = getterName + "()";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }

    final PsiMethod targetMethod = myNameToGetter.get(getterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if(methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private PsiMethodCallExpression checkMethodResolvable(PsiMethodCallExpression methodCall, final PsiMethod targetMethod, PsiReferenceExpression context) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
    final PsiElement resolved = methodCall.getMethodExpression().resolve();
    if (resolved != targetMethod) {
      PsiClass containingClass;
      if (resolved instanceof PsiMethod) {
        containingClass = ((PsiMethod) resolved).getContainingClass();
      } else if (resolved instanceof PsiClass) {
        containingClass = (PsiClass)resolved;
      }
      else {
        return null;
      }
      if(containingClass != null && containingClass.isInheritor(myClass, false)) {
        final PsiExpression newMethodExpression =
                factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getMethodExpression().replace(newMethodExpression);
      } else {
        methodCall = null;
      }
    }
    return methodCall;
  }
}
