/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.extractclass;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.extractclass.usageInfo.*;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExtractClassProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.extractclass.ExtractClassProcessor");
  @NonNls public static final String REFACTORING_NAME = "Extract Delegate";

  private final PsiClass sourceClass;
  private final List<PsiField> fields;
  private final List<PsiMethod> methods;
  private final List<PsiClass> innerClasses;
  private final Set<PsiClass> innerClassesToMakePublic = new HashSet<>();
  private final List<PsiTypeParameter> typeParams = new ArrayList<>();
  private final String newPackageName;
  private final MoveDestination myMoveDestination;
  private final String myNewVisibility;
  private final boolean myGenerateAccessors;
  private final List<PsiField> enumConstants;
  private final String newClassName;
  private final String delegateFieldName;
  private final boolean requiresBackpointer;
  private boolean delegationRequired;
  private final ExtractEnumProcessor myExtractEnumProcessor;
  private final PsiClass myClass;
  private boolean extractInnerClass;

  public ExtractClassProcessor(PsiClass sourceClass,
                               List<PsiField> fields,
                               List<PsiMethod> methods,
                               List<PsiClass> innerClasses,
                               String newPackageName,
                               String newClassName) {
    this(sourceClass, fields, methods, innerClasses, newPackageName, null, newClassName, null, false, Collections.emptyList());
  }

  public ExtractClassProcessor(PsiClass sourceClass,
                               List<PsiField> fields,
                               List<PsiMethod> methods,
                               List<PsiClass> classes,
                               String packageName,
                               MoveDestination moveDestination,
                               String newClassName,
                               String newVisibility,
                               boolean generateAccessors, List<MemberInfo> enumConstants) {
    super(sourceClass.getProject());
    this.sourceClass = sourceClass;
    this.newPackageName = packageName;
    myMoveDestination = moveDestination;
    myNewVisibility = newVisibility;
    myGenerateAccessors = generateAccessors;
    this.enumConstants = new ArrayList<>();
    for (MemberInfo constant : enumConstants) {
      if (constant.isChecked()) {
        this.enumConstants.add((PsiField)constant.getMember());
      }
    }
    this.fields = new ArrayList<>(fields);
    this.methods = new ArrayList<>(methods);
    this.innerClasses = new ArrayList<>(classes);
    this.newClassName = newClassName;
    delegateFieldName = calculateDelegateFieldName();
    requiresBackpointer = new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass).backpointerRequired();
    if (requiresBackpointer) {
      ContainerUtil.addAll(typeParams, sourceClass.getTypeParameters());
    }
    else {
      final Set<PsiTypeParameter> typeParamSet = new HashSet<>();
      final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
      for (PsiField field : fields) {
        field.accept(visitor);
      }
      for (PsiMethod method : methods) {
        method.accept(visitor);
        //do not include method's type parameters in class signature
        typeParamSet.removeAll(Arrays.asList(method.getTypeParameters()));
      }
      typeParams.addAll(typeParamSet);
    }
    myClass = new WriteCommandAction<PsiClass>(myProject, getCommandName()){
      @Override
      protected void run(@NotNull Result<PsiClass> result) throws Throwable {
        result.setResult(buildClass(false));
      }
    }.execute().getResultObject();
    myExtractEnumProcessor = new ExtractEnumProcessor(myProject, this.enumConstants, myClass);
  }

  public PsiClass getCreatedClass() {
    return myClass;
  }

  public void setExtractInnerClass(boolean extractInnerClass) {
    this.extractInnerClass = extractInnerClass;
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    myExtractEnumProcessor.findEnumConstantConflicts(refUsages);
    if (!DestinationFolderComboBox.isAccessible(myProject, sourceClass.getContainingFile().getVirtualFile(),
                                                myClass.getContainingFile().getContainingDirectory().getVirtualFile())) {
      conflicts.putValue(sourceClass, "Extracted class won't be accessible in " + RefactoringUIUtil.getDescription(sourceClass, true));
    }
    ApplicationManager.getApplication().runWriteAction(() -> myClass.delete());
    final Project project = sourceClass.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass existingClass =
      JavaPsiFacade.getInstance(project).findClass(getQualifiedName(), scope);
    if (existingClass != null) {
      conflicts.putValue(existingClass, RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
    }

    if (!myGenerateAccessors) {
      calculateInitializersConflicts(conflicts);
      final NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4ExtractedClass();
      final NecessaryAccessorsVisitor srcVisitor = checkNecessaryGettersSetters4SourceClass();
      final Set<PsiField> fieldsNeedingGetter = new LinkedHashSet<>();
      fieldsNeedingGetter.addAll(visitor.getFieldsNeedingGetter());
      fieldsNeedingGetter.addAll(srcVisitor.getFieldsNeedingGetter());
      for (PsiField field : fieldsNeedingGetter) {
        conflicts.putValue(field, "Field \'" + field.getName() + "\' needs getter");
      }
      final Set<PsiField> fieldsNeedingSetter = new LinkedHashSet<>();
      fieldsNeedingSetter.addAll(visitor.getFieldsNeedingSetter());
      fieldsNeedingSetter.addAll(srcVisitor.getFieldsNeedingSetter());
      for (PsiField field : fieldsNeedingSetter) {
        conflicts.putValue(field, "Field \'" + field.getName() + "\' needs setter");
      }
    }
    checkConflicts(refUsages, conflicts);
    return showConflicts(conflicts, refUsages.get());
  }

  private String getQualifiedName() {
    return extractInnerClass ? newClassName : StringUtil.getQualifiedName(newPackageName, newClassName);
  }


  private void calculateInitializersConflicts(MultiMap<PsiElement, String> conflicts) {
    final PsiClassInitializer[] initializers = sourceClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      if (initializerDependsOnMoved(initializer)) {
        conflicts.putValue(initializer, "Class initializer requires moved members");
      }
    }
    for (PsiMethod constructor : sourceClass.getConstructors()) {
      if (initializerDependsOnMoved(constructor.getBody())) {
        conflicts.putValue(constructor, "Constructor requires moved members");
      }
    }
  }

  private boolean initializerDependsOnMoved(PsiElement initializer) {
    final boolean [] dependsOnMoved = new boolean[]{false};
    initializer.accept(new JavaRecursiveElementWalkingVisitor(){
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiMember) {
          dependsOnMoved[0] |= !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC) && isInMovedElement(resolved);
        }
      }
    });
    return dependsOnMoved[0];
  }

  private String calculateDelegateFieldName() {
    final Project project = sourceClass.getProject();
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final JavaCodeStyleSettings settings = settingsManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class);

    final String baseName = settings.FIELD_NAME_PREFIX.length() == 0 ? StringUtil.decapitalize(newClassName) : newClassName;
    String name = settings.FIELD_NAME_PREFIX + baseName + settings.FIELD_NAME_SUFFIX;
    if (!existsFieldWithName(name) && !PsiNameHelper.getInstance(project).isKeyword(name)) {
      return name;
    }
    int counter = 1;
    while (true) {
      name = settings.FIELD_NAME_PREFIX + baseName + counter + settings.FIELD_NAME_SUFFIX;
      if (!existsFieldWithName(name) && !PsiNameHelper.getInstance(project).isKeyword(name)) {
        return name;
      }
      counter++;
    }
  }


  private boolean existsFieldWithName(String name) {
    final PsiField[] allFields = sourceClass.getAllFields();
    for (PsiField field : allFields) {
      if (name.equals(field.getName()) && !fields.contains(field)) {
        return true;
      }
    }
    return false;
  }

  protected String getCommandName() {
    return RefactorJBundle.message("extracted.class.command.name", newClassName);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usageInfos) {
    return new ExtractClassUsageViewDescriptor(sourceClass);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
    final PsiClass psiClass = buildClass(true);
    if (psiClass == null) return;
    if (delegationRequired) {
      buildDelegate();
    }
    myExtractEnumProcessor.performEnumConstantTypeMigration(usageInfos);
    final Set<PsiMember> members = new HashSet<>();
    for (PsiMethod method : methods) {
      final PsiMethod member = psiClass.findMethodBySignature(method, false);
      if (member != null) {
        members.add(member);
      }
    }
    for (PsiField field : fields) {
      final PsiField member = psiClass.findFieldByName(field.getName(), false);
      if (member != null) {
        members.add(member);
        final PsiExpression initializer = member.getInitializer();
        if (initializer != null) {
          final boolean[] moveInitializerToConstructor = new boolean[1];
          initializer.accept(new JavaRecursiveElementWalkingVisitor(){
            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
              super.visitReferenceExpression(expression);
              final PsiElement resolved = expression.resolve();
              if (resolved instanceof PsiField && !members.contains(resolved)) {
                moveInitializerToConstructor[0] = true;
              }
            }
          });

          if (moveInitializerToConstructor[0]) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
            PsiMethod[] constructors = psiClass.getConstructors();
            if (constructors.length == 0) {
              final PsiMethod constructor = (PsiMethod)elementFactory.createConstructor().setName(psiClass.getName());
              constructors = new PsiMethod[] {(PsiMethod)psiClass.add(constructor)};
            }
            for (PsiMethod constructor : constructors) {
              MoveInstanceMembersUtil.moveInitializerToConstructor(elementFactory, constructor, member);
            }
          }
        }
      }
    }

    if (myGenerateAccessors) {
      final NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4SourceClass();
      for (PsiField field : visitor.getFieldsNeedingGetter()) {
        sourceClass.add(GenerateMembersUtil.generateGetterPrototype(field));
      }

      for (PsiField field : visitor.getFieldsNeedingSetter()) {
        sourceClass.add(GenerateMembersUtil.generateSetterPrototype(field));
      }
    }
    super.performRefactoring(usageInfos);
    if (myNewVisibility == null) return;
    for (PsiMember member : members) {
      VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usageInfos), member, myNewVisibility);
    }
  }

  private NecessaryAccessorsVisitor checkNecessaryGettersSetters4SourceClass() {
    final NecessaryAccessorsVisitor visitor = new NecessaryAccessorsVisitor() {
      @Override
      protected boolean hasGetterOrSetter(PsiMethod[] getters) {
        for (PsiMethod getter : getters) {
          if (!isInMovedElement(getter)) return true;
        }
        return false;
      }

      @Override
      protected boolean isProhibitedReference(PsiField field) {
        if (fields.contains(field)) {
          return false;
        }
        if (innerClasses.contains(field.getContainingClass())) {
          return false;
        }
        return true;
      }
    };
    for (PsiField field : fields) {
      field.accept(visitor);
    }
    for (PsiMethod method : methods) {
      method.accept(visitor);
    }
    for (PsiClass innerClass : innerClasses) {
      innerClass.accept(visitor);
    }
    return visitor;
  }

  private NecessaryAccessorsVisitor checkNecessaryGettersSetters4ExtractedClass() {
    final NecessaryAccessorsVisitor visitor = new NecessaryAccessorsVisitor() {
      @Override
      protected boolean hasGetterOrSetter(PsiMethod[] getters) {
        for (PsiMethod getter : getters) {
          if (isInMovedElement(getter)) return true;
        }
        return false;
      }

      @Override
      protected boolean isProhibitedReference(PsiField field) {
        if (fields.contains(field)) {
          return true;
        }
        if (innerClasses.contains(field.getContainingClass())) {
          return true;
        }
        return false;
      }

      @Override
      public void visitMethod(PsiMethod method) {
        if (methods.contains(method)) return;
        super.visitMethod(method);
      }

      @Override
      public void visitField(PsiField field) {
        if (fields.contains(field)) return;
        super.visitField(field);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        if (innerClasses.contains(aClass)) return;
        super.visitClass(aClass);
      }

    };
    sourceClass.accept(visitor);
    return visitor;
  }


  private void buildDelegate() {
    final PsiManager manager = sourceClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    @NonNls final StringBuilder fieldBuffer = new StringBuilder();
    final String delegateVisibility = calculateDelegateVisibility();
    if (delegateVisibility.length() > 0) fieldBuffer.append(delegateVisibility).append(' ');
    fieldBuffer.append("final ");
    final String fullyQualifiedName = getQualifiedName();
    fieldBuffer.append(fullyQualifiedName);
    if (!typeParams.isEmpty()) {
      fieldBuffer.append('<');
      for (PsiTypeParameter typeParameter : typeParams) {
        fieldBuffer.append(typeParameter.getName());
      }
      fieldBuffer.append('>');
    }
    fieldBuffer.append(' ');
    fieldBuffer.append(delegateFieldName);
    fieldBuffer.append(" = new ").append(fullyQualifiedName);
    if (!typeParams.isEmpty()) {
      fieldBuffer.append('<');
      for (PsiTypeParameter typeParameter : typeParams) {
        fieldBuffer.append(typeParameter.getName());
      }
      fieldBuffer.append('>');
    }
    fieldBuffer.append('(');
    if (requiresBackpointer) {
      fieldBuffer.append("this");
    }

    fieldBuffer.append(");");
    try {
      final String fieldString = fieldBuffer.toString();
      final PsiField field = factory.createFieldFromText(fieldString, sourceClass);
      final PsiElement newField = sourceClass.add(field);
      codeStyleManager.reformat(JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newField));
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  @NonNls
  private String calculateDelegateVisibility() {
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PUBLIC) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "public";
      }
    }
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PROTECTED) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "protected";
      }
    }
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "";
      }
    }
    return "private";
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    for (PsiField field : fields) {
      findUsagesForField(field, usages);
      usages.add(new RemoveField(field));
    }
    usages.addAll(myExtractEnumProcessor.findEnumConstantUsages(new ArrayList<>(usages)));
    for (PsiClass innerClass : innerClasses) {
      findUsagesForInnerClass(innerClass, usages);
      usages.add(new RemoveInnerClass(innerClass));
    }
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        findUsagesForStaticMethod(method, usages);
      }
      else {
        findUsagesForMethod(method, usages);
      }
    }
  }

  private void findUsagesForInnerClass(PsiClass innerClass, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = innerClass.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(innerClass, scope);
    final String innerName = innerClass.getQualifiedName();
    assert innerName != null;
    final String sourceClassQualifiedName = sourceClass.getQualifiedName();
    assert sourceClassQualifiedName != null;
    final String newInnerClassName = getQualifiedName() + innerName.substring(sourceClassQualifiedName.length());
    boolean hasExternalReference = false;
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement) {
        if (!isInMovedElement(referenceElement)) {
          usages.add(new ReplaceClassReference((PsiJavaCodeReferenceElement)referenceElement, newInnerClassName));
          hasExternalReference = true;
        }
      }
    }
    if (hasExternalReference) {
      innerClassesToMakePublic.add(innerClass);
    }
  }

  private void findUsagesForMethod(PsiMethod method, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = method.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();
      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
        if (isInMovedElement(call)) {
          continue;
        }
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          usages.add(new ReplaceThisCallWithDelegateCall(call, delegateFieldName));
        }
        delegationRequired = true;
      }
    }

    if (!delegationRequired && MethodInheritanceUtils.hasSiblingMethods(method)) {
      delegationRequired = true;
    }

    if (delegationRequired) {
      usages.add(new MakeMethodDelegate(method, delegateFieldName));
    }
    else {
      usages.add(new RemoveMethod(method));
    }
  }

  private void findUsagesForStaticMethod(PsiMethod method, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = method.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
    final String fullyQualifiedName = getQualifiedName();
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();

      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
        if (!isInMovedElement(call)) {
          usages.add(new RetargetStaticMethodCall(call, fullyQualifiedName));
        }
      } else if (parent instanceof PsiImportStaticStatement) {
        final PsiJavaCodeReferenceElement importReference = ((PsiImportStaticStatement)parent).getImportReference();
        if (importReference != null) {
          final PsiElement qualifier = importReference.getQualifier();
          if (qualifier instanceof PsiJavaCodeReferenceElement) {
            usages.add(new ReplaceClassReference((PsiJavaCodeReferenceElement)qualifier, fullyQualifiedName));
          }
        }
      }
    }
    usages.add(new RemoveMethod(method));
  }

  private boolean isInMovedElement(PsiElement exp) {
    for (PsiField field : fields) {
      if (PsiTreeUtil.isAncestor(field, exp, false)) {
        return true;
      }
    }
    for (PsiMethod method : methods) {
      if (PsiTreeUtil.isAncestor(method, exp, false)) {
        return true;
      }
    }
    return false;
  }

  private void findUsagesForField(PsiField field, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = field.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    final String qualifiedName = getQualifiedName();
    @NonNls String getter = null;
    if (myGenerateAccessors) {
      getter = GenerateMembersUtil.suggestGetterName(field);
    } else {
      final PsiMethod fieldGetter = PropertyUtil.findPropertyGetter(sourceClass, field.getName(), false, false);
      if (fieldGetter != null && isInMovedElement(fieldGetter)) {
        getter = fieldGetter.getName();
      }
    }

    @NonNls String setter = null;
    if (myGenerateAccessors) {
      setter = GenerateMembersUtil.suggestSetterName(field);
    } else {
      final PsiMethod fieldSetter = PropertyUtil.findPropertySetter(sourceClass, field.getName(), false, false);
      if (fieldSetter != null && isInMovedElement(fieldSetter)) {
        setter = fieldSetter.getName();
      }
    }
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);

    for (PsiReference reference : ReferencesSearch.search(field, scope)) {
      final PsiElement element = reference.getElement();
      if (isInMovedElement(element)) {
        continue;
      }

      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression exp = (PsiReferenceExpression)element;
        if (PsiUtil.isIncrementDecrementOperation(exp.getParent())) {
          usages.add(isStatic
                     ? new ReplaceStaticVariableIncrementDecrement(exp, qualifiedName)
                     : new ReplaceInstanceVariableIncrementDecrement(exp, delegateFieldName, setter, getter, field.getName()));
        }
        else if (RefactoringUtil.isAssignmentLHS(exp)) {
          usages.add(isStatic
                     ? new ReplaceStaticVariableAssignment(exp, qualifiedName)
                     : new ReplaceInstanceVariableAssignment(PsiTreeUtil.getParentOfType(exp, PsiAssignmentExpression.class),
                                                             delegateFieldName, setter, getter, field.getName()));

        }
        else {
          usages.add(isStatic
                     ? new ReplaceStaticVariableAccess(exp, qualifiedName, enumConstants.contains(field))
                     : new ReplaceInstanceVariableAccess(exp, delegateFieldName, getter, field.getName()));
        }

        if (!isStatic) {
          delegationRequired = true;
        }
      } else if (element instanceof PsiDocTagValue) {
        usages.add(new BindJavadocReference(element, qualifiedName, field.getName()));
      }
    }
  }


  private PsiClass buildClass(boolean normalizeDeclaration) {
    final PsiManager manager = sourceClass.getManager();
    final Project project = sourceClass.getProject();
    final ExtractedClassBuilder extractedClassBuilder = new ExtractedClassBuilder();
    extractedClassBuilder.setProject(myProject);
    extractedClassBuilder.setClassName(newClassName);
    extractedClassBuilder.setPackageName(newPackageName);
    extractedClassBuilder.setOriginalClassName(sourceClass.getQualifiedName());
    extractedClassBuilder.setRequiresBackPointer(requiresBackpointer);
    extractedClassBuilder.setExtractAsEnum(enumConstants);
    for (PsiField field : fields) {
      extractedClassBuilder.addField(field);
    }
    for (PsiMethod method : methods) {
      extractedClassBuilder.addMethod(method);
    }
    for (PsiClass innerClass : innerClasses) {
      extractedClassBuilder.addInnerClass(innerClass, innerClassesToMakePublic.contains(innerClass));
    }
    extractedClassBuilder.setTypeArguments(typeParams);
    final List<PsiClass> interfaces = calculateInterfacesSupported();
    extractedClassBuilder.setInterfaces(interfaces);

    if (myGenerateAccessors) {
      final NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4ExtractedClass();
      sourceClass.accept(visitor);
      extractedClassBuilder.setFieldsNeedingGetters(visitor.getFieldsNeedingGetter());
      extractedClassBuilder.setFieldsNeedingSetters(visitor.getFieldsNeedingSetter());
    }

    final String classString = extractedClassBuilder.buildBeanClass(normalizeDeclaration);
    if (extractInnerClass) {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(newClassName + ".java", JavaFileType.INSTANCE, classString);
      final PsiClass psiClass = newFile.getClasses()[0];
      if (!psiClass.isEnum()) {
        final PsiModifierList modifierList = psiClass.getModifierList();
        assert modifierList != null;
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
      }
      final PsiElement addedClass = sourceClass.add(psiClass);
      return (PsiClass)CodeStyleManager.getInstance(manager)
        .reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedClass));
    }

    try {
      final PsiFile containingFile = sourceClass.getContainingFile();
      final PsiDirectory directory;
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (myMoveDestination != null) {
        directory = myMoveDestination.getTargetDirectory(containingDirectory);
      } else {
        final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
        assert module != null;
        directory = PackageUtil.findOrCreateDirectoryForPackage(module, newPackageName, containingDirectory, false, true);
      }
      if (directory != null) {
        final PsiFileFactory factory = PsiFileFactory.getInstance(project);
        final PsiFile newFile = factory.createFileFromText(newClassName + ".java", JavaFileType.INSTANCE, classString);
        final PsiElement addedFile = directory.add(newFile);
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedFile);
        return ((PsiJavaFile)codeStyleManager.reformat(shortenedFile)).getClasses()[0];
      } else {
        return null;
      }
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  private List<PsiClass> calculateInterfacesSupported() {
    final List<PsiClass> out = new ArrayList<>();
    final PsiClass[] supers = sourceClass.getSupers();
    for (PsiClass superClass : supers) {
      if (!superClass.isInterface()) {
        continue;
      }
      final PsiMethod[] superclassMethods = superClass.getMethods();
      if (superclassMethods.length == 0) {
        continue;
      }
      boolean allMethodsCovered = true;

      for (PsiMethod method : superclassMethods) {
        boolean isCovered = false;
        for (PsiMethod movedMethod : methods) {
          if (isSuperMethod(method, movedMethod)) {
            isCovered = true;
            break;
          }
        }
        if (!isCovered) {
          allMethodsCovered = false;
          break;
        }
      }
      if (allMethodsCovered) {
        out.add(superClass);
      }
    }
    final Project project = sourceClass.getProject();
    final PsiManager manager = sourceClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    if (usesDefaultSerialization(sourceClass)) {
      final PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", scope);
      out.add(serializable);
    }
    if (usesDefaultClone(sourceClass)) {
      final PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Cloneable", scope);
      out.add(cloneable);
    }
    return out;
  }

  private static boolean isSuperMethod(PsiMethod method, PsiMethod movedMethod) {
    final PsiMethod[] superMethods = movedMethod.findSuperMethods();
    for (PsiMethod testMethod : superMethods) {
      if (testMethod.equals(method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean usesDefaultClone(PsiClass aClass) {
    final Project project = aClass.getProject();
    final PsiManager manager = aClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Cloneable", scope);
    if (!InheritanceUtil.isInheritorOrSelf(aClass, cloneable, true)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("clone", false);
    for (PsiMethod method : methods) {
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean usesDefaultSerialization(PsiClass aClass) {
    final Project project = aClass.getProject();
    final PsiManager manager = aClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", scope);
    if (!InheritanceUtil.isInheritorOrSelf(aClass, serializable, true)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("writeObject", false);
    for (PsiMethod method : methods) {
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 1) {
        final PsiType type = parameters[0].getType();
        final String text = type.getCanonicalText();
        if ("java.io.DataOutputStream".equals(text)) {
          return false;
        }
      }
    }
    return true;
  }

  private abstract class NecessaryAccessorsVisitor extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> fieldsNeedingGetter = new HashSet<>();
    private final Set<PsiField> fieldsNeedingSetter = new HashSet<>();

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (isProhibitedReference(expression)) {
        final PsiField field = getReferencedField(expression);
        if (!hasGetter(field) && !isStaticFinal(field) && !field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
          fieldsNeedingGetter.add(field);
        }
      }
    }

    private boolean isStaticFinal(PsiField field) {
      final PsiModifierList modifierList = field.getModifierList();
      logger.assertTrue(modifierList != null);
      return modifierList.hasModifierProperty(PsiModifier.STATIC) && modifierList.hasModifierProperty(PsiModifier.FINAL);
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);

      final PsiExpression lhs = expression.getLExpression();
      if (isProhibitedReference(lhs)) {
        final PsiField field = getReferencedField(lhs);
        if (!hasGetter(field) && !isStaticFinal(field) && !field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
          fieldsNeedingSetter.add(field);
        }
      }
    }

    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
    }

    private void checkSetterNeeded(final PsiExpression operand, final PsiJavaToken sign) {
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      if (isProhibitedReference(operand)) {
        final PsiField field = getReferencedField(operand);
        if (!hasSetter(field) && !isStaticFinal(field)) {
          fieldsNeedingSetter.add(field);
        }
      }
    }

    public Set<PsiField> getFieldsNeedingGetter() {
      return fieldsNeedingGetter;
    }

    public Set<PsiField> getFieldsNeedingSetter() {
      return fieldsNeedingSetter;
    }

    private boolean hasGetter(final PsiField field) {
      return hasGetterOrSetter(sourceClass.findMethodsBySignature(GenerateMembersUtil.generateGetterPrototype(field), false));
    }

    private boolean hasSetter(final PsiField field) {
      return hasGetterOrSetter(sourceClass.findMethodsBySignature(GenerateMembersUtil.generateSetterPrototype(field), false));
    }

    protected abstract boolean hasGetterOrSetter(final PsiMethod[] getters);

    protected boolean isProhibitedReference(PsiExpression expression) {
      return BackpointerUtil.isBackpointerReference(expression, field -> this.isProhibitedReference(field));
    }

    protected abstract boolean isProhibitedReference(PsiField field);

    private PsiField getReferencedField(PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression) {
        final PsiExpression contents = ((PsiParenthesizedExpression)expression).getExpression();
        return getReferencedField(contents);
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
      return (PsiField)reference.resolve();
    }
  }
}
