// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsProcessor.class);

  private PsiClass myClass;
  @NotNull
  private final EncapsulateFieldsDescriptor myDescriptor;
  private final FieldDescriptor[] myFieldDescriptors;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project, @NotNull EncapsulateFieldsDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myFieldDescriptors = descriptor.getSelectedFields();
    myClass = descriptor.getTargetClass();
  }

  public static void setNewFieldVisibility(PsiField field, EncapsulateFieldsDescriptor descriptor) {
    try {
      if (descriptor.getFieldsVisibility() != null) {
        field.normalizeDeclaration();
        PsiUtil.setModifierProperty(field, descriptor.getFieldsVisibility(), true);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.encapsulateFields";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    final List<PsiElement> fields = new ArrayList<>();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      fields.add(fieldDescriptor.getField());
    }
    data.addElements(fields);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    List<PsiElement> elements = new ArrayList<>();
    if (myNameToGetter != null) {
      elements.addAll(myNameToGetter.values());
    }
    if (myNameToSetter != null) {
      elements.addAll(myNameToSetter.values());
    }
    data.addElements(elements);
    return data;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new EncapsulateFieldsViewDescriptor(myFieldDescriptors.clone());
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("encapsulate.fields.command.name", DescriptiveNameUtil.getDescriptiveName(myClass));
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    checkExistingMethods(conflicts, true);
    checkExistingMethods(conflicts, false);
    final Collection<PsiClass> classes = ClassInheritorsSearch.search(myClass).findAll();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final Set<PsiMethod> setters = new HashSet<>();
      final Set<PsiMethod> getters = new HashSet<>();

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
          if (place instanceof PsiReferenceExpression) {
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
                String accessorExistsMessage = JavaRefactoringBundle.message("encapsulate.fields.existed.accessor.hides.generated",
                                                                             RefactoringUIUtil.getDescription(overridden, true),
                                                                             place.getText());
                conflicts.putValue(overridden, accessorExistsMessage);
                break;
              }
            }
          }
        }
      }
    }

    UsageInfo[] infos = refUsages.get();
    for (UsageInfo info : infos) {
      PsiElement element = info.getElement();
      if (element != null) {
        PsiElement parent = element.getParent();
        if (PsiUtil.isIncrementDecrementOperation(parent) && !(parent.getParent() instanceof PsiExpressionStatement)) {
          conflicts.putValue(parent, JavaRefactoringBundle.message("encapsulate.fields.expression.type.is.used"));
        }
      }
    }

    return showConflicts(conflicts, infos);
  }

  private void checkExistingMethods(MultiMap<PsiElement, String> conflicts, boolean isGetter) {
    if (isGetter) {
      if (!myDescriptor.isToEncapsulateGet()) return;
    }
    else {
      if (!myDescriptor.isToEncapsulateSet()) return;
    }

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      PsiMethod prototype = isGetter
                            ? descriptor.getGetterPrototype()
                            : descriptor.getSetterPrototype();

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
                           JavaRefactoringBundle.message("encapsulate.fields.getter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
                                                CommonRefactoringUtil.htmlEmphasize(prototype.getName())) :
                           JavaRefactoringBundle.message("encapsulate.fields.setter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
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
              if (place instanceof PsiReferenceExpression) {
                final PsiExpression qualifierExpression = ((PsiReferenceExpression)place).getQualifierExpression();
                final PsiClass inheritor;
                if (qualifierExpression == null) {
                  inheritor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
                } else {
                  inheritor = PsiUtil.resolveClassInType(qualifierExpression.getType());
                }

                if (InheritanceUtil.isInheritorOrSelf(inheritor, myClass, true)) {
                  String accessorExistsMessage = JavaRefactoringBundle.message("encapsulate.fields.existed.accessor.hidden",
                                                                               RefactoringUIUtil.getDescription(existing, true), isGetter);
                  conflicts.putValue(existing, accessorExistsMessage);
                  break;
                }
              }
            }
          }
          containingClass = containingClass.getContainingClass();
        }
      }
    }
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    ArrayList<EncapsulateFieldUsageInfo> array = new ArrayList<>();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      for (final PsiReference reference : getFieldReferences(fieldDescriptor.getField())) {
        final PsiElement element = reference.getElement();

        final EncapsulateFieldHelper helper = EncapsulateFieldHelper.getHelper(element.getLanguage());
        if (helper != null) {
          EncapsulateFieldUsageInfo usageInfo = helper.createUsage(myDescriptor, fieldDescriptor, reference);
          if (usageInfo != null) {
            array.add(usageInfo);
          }
        }
      }
    }
    EncapsulateFieldUsageInfo[] usageInfos = array.toArray(new EncapsulateFieldUsageInfo[0]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected Iterable<? extends PsiReference> getFieldReferences(@NotNull PsiField field) {
    return ReferencesSearch.search(field);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == myFieldDescriptors.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFieldDescriptors[idx].refreshField((PsiField)element);
    }

    myClass = myFieldDescriptors[0].getField().getContainingClass();
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    updateFieldVisibility();
    generateAccessors();
    processUsagesPerFile(usages);
  }

  private void updateFieldVisibility() {
    if (myDescriptor.getFieldsVisibility() == null) return;

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      setNewFieldVisibility(descriptor.getField(), myDescriptor);
    }
  }

  private void generateAccessors() {
    // generate accessors
    myNameToGetter = new HashMap<>();
    myNameToSetter = new HashMap<>();

    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final DocCommentPolicy<PsiDocComment> commentPolicy = new DocCommentPolicy<>(myDescriptor.getJavadocPolicy());

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
    Map<PsiFile, List<EncapsulateFieldUsageInfo>> usagesInFiles = new HashMap<>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      final PsiFile file = element.getContainingFile();
      List<EncapsulateFieldUsageInfo> usagesInFile = usagesInFiles.get(file);
      if (usagesInFile == null) {
        usagesInFile = new ArrayList<>();
        usagesInFiles.put(file, usagesInFile);
      }
      usagesInFile.add(((EncapsulateFieldUsageInfo)usage));
    }

    for (List<EncapsulateFieldUsageInfo> usageInfos : usagesInFiles.values()) {
      //this is to avoid elements to become invalid as a result of processUsage
      final EncapsulateFieldUsageInfo[] infos = usageInfos.toArray(new EncapsulateFieldUsageInfo[0]);
      CommonRefactoringUtil.sortDepthFirstRightLeftOrder(infos);

      for (EncapsulateFieldUsageInfo info : infos) {
        final PsiElement element = info.getElement();
        if (element == null) continue;
        EncapsulateFieldHelper helper = EncapsulateFieldHelper.getHelper(element.getLanguage());
        helper.processUsage(info,
                            myDescriptor,
                            myNameToSetter.get(info.getFieldDescriptor().getSetterName()),
                            myNameToGetter.get(info.getFieldDescriptor().getGetterName())
        );
      }
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
}
