/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveInner;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.ide.util.EditorHelper;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * created at Sep 24, 2001
 * @author Jeka
 */
public class MoveInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInner.MoveInnerProcessor");

  private MoveCallback myMoveCallback;

  private PsiClass myInnerClass;
  private PsiClass myOuterClass;
  private PsiElement myTargetContainer;
  private String myParameterNameOuterClass;
  private String myFieldNameOuterClass;
  private String myDescriptiveName = "";
  private String myNewClassName;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private boolean myOpenInEditor;

  public MoveInnerProcessor(Project project, MoveCallback moveCallback) {
    super(project);
    myMoveCallback = moveCallback;
  }

  public MoveInnerProcessor(Project project,
                            PsiClass innerClass,
                            String name,
                            boolean passOuterClass,
                            String parameterName,
                            final PsiElement targetContainer) {
    super(project);
    setup(innerClass, name, passOuterClass, parameterName, true, true, targetContainer);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.inner.class.command", myDescriptiveName);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new MoveInnerViewDescriptor(myInnerClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetContainer != null);

    Collection<PsiReference> innerClassRefs = ReferencesSearch.search(myInnerClass).findAll();
    ArrayList<UsageInfo> usageInfos = new ArrayList<>(innerClassRefs.size());
    for (PsiReference innerClassRef : innerClassRefs) {
      PsiElement ref = innerClassRef.getElement();
      if (!PsiTreeUtil.isAncestor(myInnerClass, ref, true)) { // do not show self-references
        usageInfos.add(new UsageInfo(ref));
      }
    }

    final String newQName;
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiDirectory targetDirectory = (PsiDirectory)myTargetContainer;
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
      LOG.assertTrue(aPackage != null);
      newQName = aPackage.getQualifiedName() + "." + myNewClassName;
    }
    else if (myTargetContainer instanceof PsiClass) {
      final String qName = ((PsiClass)myTargetContainer).getQualifiedName();
      if (qName != null) {
        newQName = qName + "." + myNewClassName;
      }
      else {
        newQName = myNewClassName;
      }
    }
    else {
      newQName = myNewClassName;
    }
    MoveClassesOrPackagesUtil.findNonCodeUsages(mySearchInComments, mySearchInNonJavaFiles,
                                                myInnerClass, newQName, usageInfos);
    return usageInfos.toArray(new UsageInfo[usageInfos.size()]);
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiClass;
    LOG.assertTrue(condition);
    myInnerClass = (PsiClass)elements[0];
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public void setSearchInComments(boolean searchInComments) {
    mySearchInComments = searchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  protected void performRefactoring(@NotNull final UsageInfo[] usages) {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    final RefactoringElementListener elementListener = getTransaction().getElementListener(myInnerClass);
    try {
      PsiField field = null;
      if (myParameterNameOuterClass != null) {
        // pass outer as a parameter
        field = factory.createField(myFieldNameOuterClass, factory.createType(myOuterClass));
        field = addOuterField(field);
        myInnerClass = field.getContainingClass();
        addFieldInitializationToConstructors(myInnerClass, field, myParameterNameOuterClass);
      }

      ChangeContextUtil.encodeContextInfo(myInnerClass, false);

      myInnerClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myInnerClass);

      final MoveInnerOptions moveInnerOptions = new MoveInnerOptions(myInnerClass, myOuterClass, myTargetContainer, myNewClassName);
      final MoveInnerHandler handler = MoveInnerHandler.EP_NAME.forLanguage(myInnerClass.getLanguage());
      final PsiClass newClass;
      try {
        newClass = handler.copyClass(moveInnerOptions);
      }
      catch (IncorrectOperationException e) {
        RefactoringUIUtil.processIncorrectOperation(myProject, e);
        return;
      }

      // replace references in a new class to old inner class with references to itself
      for (PsiReference ref : ReferencesSearch.search(myInnerClass, new LocalSearchScope(newClass), true)) {
        PsiElement element = ref.getElement();
        if (element.getParent() instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement)element.getParent();
          PsiElement parentRefElement = parentRef.resolve();
          if (parentRefElement instanceof PsiClass) { // reference to inner class inside our inner
            final PsiReferenceList referenceList = PsiTreeUtil.getTopmostParentOfType(parentRef, PsiReferenceList.class);
            if (referenceList == null || referenceList.getParent() != newClass) {
              parentRef.getQualifier().delete();
              continue;
            }
          }
        }
        ref.bindToElement(newClass);
      }

      List<PsiReference> referencesToRebind = new ArrayList<>();
      for (UsageInfo usage : usages) {
        if (usage.isNonCodeUsage) continue;
        PsiElement refElement = usage.getElement();
        PsiReference[] references = refElement.getReferences();
        for (PsiReference reference : references) {
          if (reference.isReferenceTo(myInnerClass)) {
            referencesToRebind.add(reference);
          }
        }
      }

      myInnerClass.delete();

      // correct references in usages
      for (UsageInfo usage : usages) {
        if (usage.isNonCodeUsage || myParameterNameOuterClass == null) continue; // should pass outer as parameter

        MoveInnerClassUsagesHandler usagesHandler = MoveInnerClassUsagesHandler.EP_NAME.forLanguage(usage.getElement().getLanguage());
        if (usagesHandler != null) {
          usagesHandler.correctInnerClassUsage(usage, myOuterClass);
        }
      }

      for (PsiReference reference : referencesToRebind) {
        reference.bindToElement(newClass);
      }

      for (UsageInfo usage : usages) {
        final PsiElement element = usage.getElement();
        final PsiElement parent = element != null ? element.getParent() : null;
        if (parent instanceof PsiNewExpression) {
          final PsiMethod resolveConstructor = ((PsiNewExpression)parent).resolveConstructor();
          for (PsiMethod method : newClass.getConstructors()) {
            if (resolveConstructor == method) {
              final PsiElement place = usage.getElement();
              if (place != null) {
                VisibilityUtil.escalateVisibility(method, place);
              }
              break;
            }
          }
        }
      }

      if (field != null) {
        final PsiExpression paramAccessExpression = factory.createExpressionFromText(myParameterNameOuterClass, null);
        for (final PsiMethod constructor : newClass.getConstructors()) {
          final PsiStatement[] statements = constructor.getBody().getStatements();
          if (statements.length > 0) {
            if (statements[0] instanceof PsiExpressionStatement) {
              PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
              if (expression instanceof PsiMethodCallExpression) {
                @NonNls String text = ((PsiMethodCallExpression)expression).getMethodExpression().getText();
                if ("this".equals(text) || "super".equals(text)) {
                  ChangeContextUtil.decodeContextInfo(expression, myOuterClass, paramAccessExpression);
                }
              }
            }
          }
        }

        PsiExpression accessExpression = factory.createExpressionFromText(myFieldNameOuterClass, null);
        ChangeContextUtil.decodeContextInfo(newClass, myOuterClass, accessExpression);
      }
      else {
        ChangeContextUtil.decodeContextInfo(newClass, null, null);
      }

      if (myOpenInEditor) {
        EditorHelper.openInEditor(newClass);
      }

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
      elementListener.elementMoved(newClass);

      List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<>();
      for (UsageInfo usage : usages) {
        if (usage instanceof NonCodeUsageInfo) {
          nonCodeUsages.add((NonCodeUsageInfo)usage);
        }
      }
      myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiField addOuterField(PsiField field) {
    final PsiMember[] members = PsiTreeUtil.getChildrenOfType(myInnerClass, PsiMember.class);
    if (members != null) {
      for (PsiMember member : members) {
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          return (PsiField)myInnerClass.addBefore(field, member);
        }
      }
    }

    return (PsiField)myInnerClass.add(field);
  }

  protected void performPsiSpoilingRefactoring() {
    if (myNonCodeUsages != null) {
      RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    }
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final HashMap<PsiElement,HashSet<PsiElement>> reported = new HashMap<>();
    class Visitor extends JavaRecursiveElementWalkingVisitor {


      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiMember &&
            PsiTreeUtil.isAncestor(myInnerClass, resolved, true) &&
            becomesInaccessible((PsiMember)resolved)) {
          registerConflict(reference, resolved, reported, conflicts);
        }
      }




      @Override public void visitClass(PsiClass aClass) {
        if (aClass == myInnerClass) return;
        super.visitClass(aClass);
      }
    }

//    if (myInnerClass.hasModifierProperty(PsiModifier.)) {
    myOuterClass.accept(new Visitor());
    myInnerClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiMember) {
          if (PsiTreeUtil.isAncestor(myOuterClass, resolve, true) && !PsiTreeUtil.isAncestor(myInnerClass, resolve, false)) {
            if (becomesInaccessible((PsiMember)resolve)) {
              registerConflict(reference, resolve, reported, conflicts);
            }
          }
        }
      }
    });

    return showConflicts(conflicts, refUsages.get());
  }

  private static void registerConflict(PsiJavaCodeReferenceElement reference,
                                       PsiElement resolved,
                                       HashMap<PsiElement, HashSet<PsiElement>> reported, MultiMap<PsiElement, String> conflicts) {
    final PsiElement container = ConflictsUtil.getContainer(reference);
    HashSet<PsiElement> containerSet = reported.get(container);
    if (containerSet == null) {
      containerSet = new HashSet<>();
      reported.put(container, containerSet);
    }
    if (!containerSet.contains(resolved)) {
      containerSet.add(resolved);
      String placesDescription;
      if (containerSet.size() == 1) {
        placesDescription = RefactoringUIUtil.getDescription(resolved, true);
      } else {
        placesDescription = "<ol><li>" + StringUtil.join(containerSet, element -> RefactoringUIUtil.getDescription(element, true), "</li><li>") + "</li></ol>";
      }
      String message = RefactoringBundle.message("0.will.become.inaccessible.from.1",
                                                 placesDescription,
                                                 RefactoringUIUtil.getDescription(container, true));
      conflicts.put(container, Collections.singletonList(message));
    }
  }

  private boolean becomesInaccessible(PsiMember element) {
    final String visibilityModifier = VisibilityUtil.getVisibilityModifier(element.getModifierList());
    if (PsiModifier.PRIVATE.equals(visibilityModifier)) return true;
    if (PsiModifier.PUBLIC.equals(visibilityModifier)) return false;
    if (PsiModifier.PROTECTED.equals(visibilityModifier) &&
        InheritanceUtil.isInheritorOrSelf(myInnerClass, myOuterClass, true)) {
      return false;
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myTargetContainer);
      assert aPackage != null : myTargetContainer;
      return !psiFacade.isInPackage(myOuterClass, aPackage);
    }
    // target container is a class
    PsiFile targetFile = myTargetContainer.getContainingFile();
    if (targetFile != null) {
      final PsiDirectory containingDirectory = targetFile.getContainingDirectory();
      if (containingDirectory != null) {
        final PsiPackage targetPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
        assert targetPackage != null : myTargetContainer;
        return psiFacade.isInPackage(myOuterClass, targetPackage);
      }
    }
    return false;
  }

  public void setup(final PsiClass innerClass,
                    final String className,
                    final boolean passOuterClass,
                    final String parameterName,
                    boolean searchInComments,
                    boolean searchInNonJava,
                    @NotNull final PsiElement targetContainer) {
    myNewClassName = className;
    myInnerClass = innerClass;
    myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myInnerClass);
    myOuterClass = myInnerClass.getContainingClass();
    myTargetContainer = targetContainer;
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myParameterNameOuterClass = passOuterClass ? parameterName : null;
    if (myParameterNameOuterClass != null) {
      myFieldNameOuterClass =
      codeStyleManager.variableNameToPropertyName(myParameterNameOuterClass, VariableKind.PARAMETER);
      myFieldNameOuterClass = codeStyleManager.propertyNameToVariableName(myFieldNameOuterClass, VariableKind.FIELD);
    }
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJava;
  }

  private void addFieldInitializationToConstructors(PsiClass aClass, PsiField field, String parameterName)
    throws IncorrectOperationException {

    PsiMethod[] constructors = aClass.getConstructors();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        if (parameterName != null) {
          PsiParameterList parameterList = constructor.getParameterList();
          PsiParameter parameter = factory.createParameter(parameterName, field.getType());
          parameterList.addAfter(parameter, null);
        }
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          PsiStatement first = statements[0];
          if (first instanceof PsiExpressionStatement) {
            PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
            if (expression instanceof PsiMethodCallExpression) {
              @NonNls String text = ((PsiMethodCallExpression)expression).getMethodExpression().getText();
              if ("this".equals(text)) {
                continue;
              }
            }
          }
        }
        createAssignmentStatement(constructor, field.getName(), parameterName);
      }
    }
    else {
      PsiMethod constructor = factory.createConstructor();
      if (parameterName != null) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter parameter = factory.createParameter(parameterName, field.getType());
        parameterList.add(parameter);
      }
      createAssignmentStatement(constructor, field.getName(), parameterName);
      aClass.add(constructor);
    }
  }

  private PsiStatement createAssignmentStatement(PsiMethod constructor, String fieldName, String parameterName)
    throws IncorrectOperationException {

    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    @NonNls String pattern = fieldName + "=a;";
    if (fieldName.equals(parameterName)) {
      pattern = "this." + pattern;
    }

    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
    statement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(statement);

    PsiCodeBlock body = constructor.getBody();
    assert body != null : constructor;
    statement = (PsiExpressionStatement)body.addAfter(statement, getAnchorElement(body));

    PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
    PsiReferenceExpression rExpr = (PsiReferenceExpression)assignment.getRExpression();
    assert rExpr != null : assignment;
    PsiIdentifier identifier = (PsiIdentifier)rExpr.getReferenceNameElement();
    assert identifier != null : assignment;
    identifier.replace(factory.createIdentifier(parameterName));
    return statement;
  }

  @Nullable
  private static PsiElement getAnchorElement(PsiCodeBlock body) {
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      PsiStatement first = statements[0];
      if (first instanceof PsiExpressionStatement) {

        PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodCall = ((PsiMethodCallExpression)expression).getMethodExpression();
          @NonNls String text = methodCall.getText();
          if ("super".equals(text)) {
            return first;
          }
        }
      }
    }
    return null;
  }

  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  public String getNewClassName() {
    return myNewClassName;
  }

  public boolean shouldPassParameter() {
    return myParameterNameOuterClass != null;
  }


  public String getParameterName() {
    return myParameterNameOuterClass;
  }

  public void setOpenInEditor(boolean openInEditor) {
    myOpenInEditor = openInEditor;
  }
}