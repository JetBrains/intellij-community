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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.OverriderUsageInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MethodReturnTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnBooleanFix");

  private final PsiType myReturnType;
  private final boolean myFixWholeHierarchy;
  private final String myName;
  private final String myCanonicalText;

  public MethodReturnTypeFix(final PsiMethod method, final PsiType returnType, boolean fixWholeHierarchy) {
    super(method);
    myReturnType = returnType;
    myFixWholeHierarchy = fixWholeHierarchy;
    myName = method.getName();
    myCanonicalText = returnType.getCanonicalText();
  }


  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("fix.return.type.text", myName, myCanonicalText);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;

    return myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myReturnType, myMethod.getReturnType());
  }

  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;

    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    if (myFixWholeHierarchy) {
      final PsiMethod superMethod = myMethod.findDeepestSuperMethod();
      final PsiType superReturnType = superMethod == null ? null : superMethod.getReturnType();
      if (superReturnType != null &&
          !Comparing.equal(myReturnType, superReturnType) &&
          !changeClassTypeArgument(myMethod, project, superReturnType, superMethod.getContainingClass(), editor)) {
        return;
      }
    }

    final List<PsiMethod> affectedMethods = changeReturnType(myMethod, myReturnType);

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final SourceMethodSelector returnSelector = new SourceMethodSelector(myMethod);
    if (!PsiType.VOID.equals(myReturnType)) {
      final ReturnStatementAdder adder = new ReturnStatementAdder(factory, myReturnType, returnSelector);

      for (PsiMethod affectedMethod : affectedMethods) {
        adder.addReturnForMethod(file, affectedMethod);
      }
    }

    final PsiReturnStatement latestReturn = returnSelector.getReturnStatement();
    if (latestReturn != null) {
      Editor editorForMethod = getEditorForMethod(myMethod, project, editor, latestReturn.getContainingFile());
      if (editorForMethod != null) {
        selectReturnValueInEditor(latestReturn, editorForMethod);
      }
    }
  }

  private static class SourceMethodSelector {
    private final PsiMethod mySourceMethod;
    private PsiReturnStatement myReturnStatement;

    private SourceMethodSelector(final PsiMethod sourceMethod) {
      mySourceMethod = sourceMethod;
    }

    public void accept(final PsiReturnStatement statement, final PsiMethod method) {
      if (mySourceMethod.equals(method) && statement != null) {
        myReturnStatement = statement;
      }
    }

    public PsiReturnStatement getReturnStatement() {
      return myReturnStatement;
    }
  }

  // to clearly separate data
  private static class ReturnStatementAdder {
    private final PsiElementFactory factory;
    private final PsiType myTargetType;
    private final SourceMethodSelector mySelector;

    private ReturnStatementAdder(@NotNull final PsiElementFactory factory, @NotNull final PsiType targetType,
                                 @NotNull final SourceMethodSelector selector) {
      this.factory = factory;
      myTargetType = targetType;
      mySelector = selector;
    }

    public void addReturnForMethod(final PsiFile file, final PsiMethod method) {
      final PsiModifierList modifiers = method.getModifierList();
      if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT) || method.getBody() == null) {
        return;
      }

      try {
        final ConvertReturnStatementsVisitor visitor = new ConvertReturnStatementsVisitor(factory, method, myTargetType);

        ControlFlow controlFlow;
        try {
          controlFlow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(method.getBody());
        }
        catch (AnalysisCanceledException e) {
          controlFlow = null; //must be an error
        }
        PsiReturnStatement returnStatement;
        if (controlFlow != null && ControlFlowUtil.processReturns(controlFlow, visitor)) {
          // extra return statement not needed
          // get latest modified return statement and select...
          returnStatement = visitor.getLatestReturn();
        }
        else {
          returnStatement = visitor.createReturnInLastStatement();
        }
        mySelector.accept(returnStatement, method);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      if (method.getContainingFile() != file) {
        UndoUtil.markPsiFileForUndo(file);
      }
    }
  }

  private static Editor getEditorForMethod(PsiMethod myMethod, @NotNull final Project project, final Editor editor, final PsiFile file) {

    PsiFile containingFile = myMethod.getContainingFile();
    if (containingFile != file) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile());
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    return editor;
  }

  @Nullable
  private PsiMethod[] getChangeRoots(final PsiMethod method) {
    if (!myFixWholeHierarchy) return new PsiMethod[]{method};

    final PsiMethod[] methods = method.findDeepestSuperMethods();

    if (methods.length > 0) {
      return methods;
    }
    // no - only base
    return new PsiMethod[] {method};
  }

  @NotNull
  private List<PsiMethod> changeReturnType(final PsiMethod method, final PsiType returnType) {
    final PsiMethod[] methods = getChangeRoots(method);
    if (methods == null) {
      // canceled
      return Collections.emptyList();
    }

    final MethodSignatureChangeVisitor methodSignatureChangeVisitor = new MethodSignatureChangeVisitor();
    for (PsiMethod targetMethod : methods) {
      methodSignatureChangeVisitor.addBase(targetMethod);
      ChangeSignatureProcessor processor = new UsagesAwareChangeSignatureProcessor(method.getProject(), targetMethod,
                                                                        false, null,
                                                                        myName,
                                                                        returnType,
                                                                        RemoveUnusedParameterFix.getNewParametersInfo(method, null),
                                                                        methodSignatureChangeVisitor);
      processor.run();
    }

    return methodSignatureChangeVisitor.getAffectedMethods();
  }

  private static class MethodSignatureChangeVisitor implements UsageVisitor {
    private final List<PsiMethod> myAffectedMethods;

    private MethodSignatureChangeVisitor() {
      myAffectedMethods = new ArrayList<PsiMethod>();
    }

    public void addBase(final PsiMethod baseMethod) {
      myAffectedMethods.add(baseMethod);
    }

    public void visit(final UsageInfo usage) {
      if (usage instanceof OverriderUsageInfo) {
        myAffectedMethods.add(((OverriderUsageInfo) usage).getElement());
      }
    }

    public List<PsiMethod> getAffectedMethods() {
      return myAffectedMethods;
    }

    public void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
      for (Iterator<UsageInfo> usageInfoIterator = covariantOverriderInfos.iterator(); usageInfoIterator.hasNext();) {
        final UsageInfo info = usageInfoIterator.next();
        if (info instanceof OverriderUsageInfo) {
          final OverriderUsageInfo overrideUsage = (OverriderUsageInfo) info;
          if (myAffectedMethods.contains(overrideUsage.getElement())) {
            usageInfoIterator.remove();
          }
        }
      }
    }
  }

  private interface UsageVisitor {
    void visit(final UsageInfo usage);
    void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos);
  }

  private static class UsagesAwareChangeSignatureProcessor extends ChangeSignatureProcessor {
    private final UsageVisitor myUsageVisitor;

    private UsagesAwareChangeSignatureProcessor(final Project project, final PsiMethod method, final boolean generateDelegate,
                                                @Modifier final String newVisibility, final String newName, final PsiType newType,
                                                @NotNull final ParameterInfoImpl[] parameterInfo, final UsageVisitor usageVisitor) {
      super(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo);
      myUsageVisitor = usageVisitor;
    }

    protected void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
      myUsageVisitor.preprocessCovariantOverriders(covariantOverriderInfos);
    }

    protected void performRefactoring(final UsageInfo[] usages) {
      super.performRefactoring(usages);

      for (UsageInfo usage : usages) {
        myUsageVisitor.visit(usage);
      }
    }
  }

  static void selectReturnValueInEditor(final PsiReturnStatement returnStatement, final Editor editor) {
    TextRange range = returnStatement.getReturnValue().getTextRange();
    int offset = range.getStartOffset();

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
  }

  private boolean changeClassTypeArgument(PsiMethod myMethod, Project project, PsiType superReturnType, PsiClass superClass, Editor editor) {
    if (superClass == null || !superClass.hasTypeParameters()) return true;
    final PsiClass superReturnTypeClass = PsiUtil.resolveClassInType(superReturnType);
    if (superReturnTypeClass == null || !(superReturnTypeClass instanceof PsiTypeParameter || superReturnTypeClass.hasTypeParameters())) return true;

    final PsiClass derivedClass = myMethod.getContainingClass();
    if (derivedClass == null) return true;

    final PsiReferenceParameterList referenceParameterList = findTypeArgumentsList(superClass, derivedClass);
    if (referenceParameterList == null) return true;

    final PsiElement resolve = ((PsiJavaCodeReferenceElement)referenceParameterList.getParent()).resolve();
    if (!(resolve instanceof PsiClass)) return true;
    final PsiClass baseClass = (PsiClass)resolve;

    PsiType returnType = myReturnType;
    if (returnType instanceof PsiPrimitiveType) {
      returnType = ((PsiPrimitiveType)returnType).getBoxedType(derivedClass);
    }

    final PsiSubstitutor superClassSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(superClass, baseClass, PsiSubstitutor.EMPTY);
    final PsiType superReturnTypeInBaseClassType = superClassSubstitutor.substitute(superReturnType);
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    final PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(baseClass.getTypeParameters(),
                                                                           new PsiType[]{superReturnTypeInBaseClassType},
                                                                           new PsiType[]{returnType},
                                                                           PsiUtil.getLanguageLevel(superClass));

    final TypeMigrationRules rules = new TypeMigrationRules(TypeMigrationLabeler.getElementType(derivedClass));
    rules.setMigrationRootType(JavaPsiFacade.getElementFactory(project).createType(baseClass, psiSubstitutor));
    rules.setBoundScope(new LocalSearchScope(derivedClass));
    TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, referenceParameterList);

    return false;
  }

  @Nullable
  private static PsiReferenceParameterList findTypeArgumentsList(final PsiClass superClass, final PsiClass derivedClass) {
    PsiReferenceParameterList referenceParameterList = null;
    if (derivedClass instanceof PsiAnonymousClass) {
      referenceParameterList = ((PsiAnonymousClass)derivedClass).getBaseClassReference().getParameterList();
    } else {
      final PsiReferenceList implementsList = derivedClass.getImplementsList();
      if (implementsList != null) {
        referenceParameterList = extractReferenceParameterList(superClass, implementsList);
      }
      if (referenceParameterList == null) {
        final PsiReferenceList extendsList = derivedClass.getExtendsList();
        if (extendsList != null) {
          referenceParameterList = extractReferenceParameterList(superClass, extendsList);
        }
      }
    }
    return referenceParameterList;
  }

  @Nullable
  private static PsiReferenceParameterList extractReferenceParameterList(final PsiClass superClass,
                                                                         final PsiReferenceList extendsList) {
    for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
      final PsiElement element = referenceElement.resolve();
      if (element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)element, superClass, true)) {
        return referenceElement.getParameterList();
      }
    }
    return null;
  }
}
