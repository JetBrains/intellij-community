
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
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
  private final PsiField[] myFields;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project, @NotNull EncapsulateFieldsDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myFields = myDescriptor.getSelectedFields();
    myClass = myFields[0].getContainingClass();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    PsiField[] fields = new PsiField[myFields.length];
    System.arraycopy(myFields, 0, fields, 0, myFields.length);
    return new EncapsulateFieldsViewDescriptor(fields);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("encapsulate.fields.command.name", UsageViewUtil.getDescriptiveName(myClass));
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    final PsiMethod[] getterPrototypes = myDescriptor.getGetterPrototypes();
    final PsiMethod[] setterPrototypes = myDescriptor.getSetterPrototypes();

    checkExistingMethods(getterPrototypes, conflicts, true);
    checkExistingMethods(setterPrototypes, conflicts, false);
    final Collection<PsiClass> classes = ClassInheritorsSearch.search(myClass).findAll();
    for (int i = 0; i < myFields.length; i++) {
      final PsiField field = myFields[i];
      final Set<PsiMethod> setters = new HashSet<PsiMethod>();
      final Set<PsiMethod> getters = new HashSet<PsiMethod>();

      for (PsiClass aClass : classes) {
        final PsiMethod getterOverrider = getterPrototypes != null ? aClass.findMethodBySignature(getterPrototypes[i], false) : null;
        if (getterOverrider != null) {
          getters.add(getterOverrider);
        }
        final PsiMethod setterOverrider = setterPrototypes != null ? aClass.findMethodBySignature(setterPrototypes[i], false) : null;
        if (setterOverrider != null) {
          setters.add(setterOverrider);
        }
      }
      if (!getters.isEmpty() || !setters.isEmpty()) {
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
                                             CommonRefactoringUtil.htmlEmphasize(RefactoringUIUtil.getDescription(overridden, true)) +
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

  private void checkExistingMethods(PsiMethod[] prototypes, MultiMap<PsiElement, String> conflicts, boolean isGetter) {
    if(prototypes == null) return;
    for (PsiMethod prototype : prototypes) {
      final PsiType prototypeReturnType = prototype.getReturnType();
      PsiMethod existing = myClass.findMethodBySignature(prototype, true);
      if (existing != null) {
        final PsiType returnType = existing.getReturnType();
        if (!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
          final String descr = PsiFormatUtil.formatMethod(existing,
                                                          PsiSubstitutor.EMPTY,
                                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_TYPE,
                                                          PsiFormatUtil.SHOW_TYPE
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
                conflicts.putValue(existing, "There is already a " + CommonRefactoringUtil.htmlEmphasize(RefactoringUIUtil.getDescription(existing, true)) + " which would be hidden by generated " + (isGetter ? "getter" : "setter"));
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
    PsiMethod[] getterPrototypes = myDescriptor.getGetterPrototypes();
    PsiMethod[] setterPrototypes = myDescriptor.getSetterPrototypes();
    ArrayList<UsageInfo> array = new ArrayList<UsageInfo>();
    PsiField[] fields = myFields;
    for(int i = 0; i < fields.length; i++){
      PsiField field = fields[i];
      for (final PsiReference reference : ReferencesSearch.search(field)) {
        if (!(reference instanceof PsiReferenceExpression)) continue;
        PsiReferenceExpression ref = (PsiReferenceExpression)reference;
        // [Jeka] to avoid recursion in the field's accessors
        if (findGet && isUsedInExistingAccessor(getterPrototypes[i], ref)) continue;
        if (findSet && isUsedInExistingAccessor(setterPrototypes[i], ref)) continue;
        if (!findGet) {
          if (!PsiUtil.isAccessedForWriting(ref)) continue;
        }
        if (!findSet || field.hasModifierProperty(PsiModifier.FINAL)) {
          if (!PsiUtil.isAccessedForReading(ref)) continue;
        }
        if (!myDescriptor.isToUseAccessorsWhenAccessible()) {
          PsiClass accessObjectClass = null;
          PsiExpression qualifier = ref.getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }
          if (facade.getResolveHelper()
            .isAccessible(field, newModifierList, ref, accessObjectClass, null)) {
            continue;
          }
        }
        UsageInfo usageInfo = new MyUsageInfo(ref, i);
        array.add(usageInfo);
      }
    }
    MyUsageInfo[] usageInfos = array.toArray(new MyUsageInfo[array.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myFields.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFields[idx] = (PsiField)element;
    }

    myClass = myFields[0].getContainingClass();
  }

  protected void performRefactoring(UsageInfo[] usages) {
    // change visibility of fields
    if (myDescriptor.getFieldsVisibility() != null){
      // "as is"
      for (PsiField field : myFields) {
        setNewFieldVisibility(field);
      }
    }

    // generate accessors
    myNameToGetter = new HashMap<String, PsiMethod>();
    myNameToSetter = new HashMap<String, PsiMethod>();
    for(int i = 0; i < myFields.length; i++){
      final DocCommentPolicy<PsiDocComment> commentPolicy = new DocCommentPolicy<PsiDocComment>(myDescriptor.getJavadocPolicy());
      PsiField field = myFields[i];
      final PsiDocComment docComment = field.getDocComment();
      if (myDescriptor.isToEncapsulateGet()){
        PsiMethod[] prototypes = myDescriptor.getGetterPrototypes();
        assert prototypes != null;
        final PsiMethod getter = addOrChangeAccessor(prototypes[i], myNameToGetter);
        if (docComment != null) {
          final PsiDocComment getterJavadoc = (PsiDocComment)getter.addBefore(docComment, getter.getFirstChild());
          commentPolicy.processNewJavaDoc(getterJavadoc);
        }
      }
      if (myDescriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL)){
        PsiMethod[] prototypes = myDescriptor.getSetterPrototypes();
        assert prototypes != null;
        addOrChangeAccessor(prototypes[i], myNameToSetter);
      }

      if (docComment != null) {
        commentPolicy.processOldJavaDoc(docComment);
      }
    }

    Map<PsiFile, List<MyUsageInfo>> usagesInFiles = new HashMap<PsiFile, List<MyUsageInfo>>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      final PsiFile file = element.getContainingFile();
      List<MyUsageInfo> usagesInFile = usagesInFiles.get(file);
      if (usagesInFile == null) {
        usagesInFile = new ArrayList<MyUsageInfo>();
        usagesInFiles.put(file, usagesInFile);
      }
      usagesInFile.add(((MyUsageInfo)usage));
    }

    for (List<MyUsageInfo> usageInfos : usagesInFiles.values()) {
      //this is to avoid elements to become invalid as a result of processUsage
      final MyUsageInfo[] infos = usageInfos.toArray(new MyUsageInfo[usageInfos.size()]);
      RefactoringUtil.sortDepthFirstRightLeftOrder(infos);

      for (MyUsageInfo info : infos) {
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

  private void processUsage(MyUsageInfo usage) {
    PsiField field = myFields[usage.fieldIndex];
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
            final int fieldIndex = usage.fieldIndex;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldIndex, setterArgument, expr);

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
              final int fieldIndex = usage.fieldIndex;
              final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
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
              setExpr = createSetterCall(usage.fieldIndex, binExpr, expr);
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
        PsiJavaToken sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationSign();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationSign();
        }
        IElementType tokenType = sign.getTokenType();

        PsiExpression getExpr = expr;
        if (processGet){
          final int fieldIndex = usage.fieldIndex;
          final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
          if(getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text;
        if (tokenType == JavaTokenType.PLUSPLUS){
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
          final int fieldIndex = usage.fieldIndex;
          setExpr = createSetterCall(fieldIndex, binExpr, expr);
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
        PsiMethodCallExpression methodCall = createGetterCall(usage.fieldIndex, expr);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private PsiMethodCallExpression createSetterCall(final int fieldIndex, final PsiExpression setterArgument, PsiReferenceExpression expr) throws IncorrectOperationException {
    String[] setterNames = myDescriptor.getSetterNames();
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String setterName = setterNames[fieldIndex];
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
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
    }
    return methodCall;
  }

  @Nullable
  private PsiMethodCallExpression createGetterCall(final int fieldIndex, PsiReferenceExpression expr)
          throws IncorrectOperationException {
    String[] getterNames = myDescriptor.getGetterNames();
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String getterName = getterNames[fieldIndex];
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
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
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



  private static class MyUsageInfo extends UsageInfo {
    public final int fieldIndex;

    public MyUsageInfo(PsiJavaCodeReferenceElement ref, int fieldIndex) {
      super(ref);
      this.fieldIndex = fieldIndex;
    }
  }
}
