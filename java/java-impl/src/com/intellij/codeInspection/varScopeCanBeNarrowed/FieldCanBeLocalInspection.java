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
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class FieldCanBeLocalInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection");

  @NonNls public static final String SHORT_NAME = "FieldCanBeLocal";

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CLASSLAYOUT_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.field.can.be.local.display.name");
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {

      }

      @Override
      public void visitJavaFile(PsiJavaFile file) {
        for (PsiClass aClass : file.getClasses()) {
          docheckClass(aClass, holder);
        }
      }
    };
  }

  private static void docheckClass(final PsiClass aClass, ProblemsHolder holder) {
    if (aClass.isInterface()) return;
    final PsiField[] fields = aClass.getFields();
    final Set<PsiField> candidates = new LinkedHashSet<PsiField>();
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PRIVATE) && !(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL))) {
        candidates.add(field);
      }
    }

    removeFieldsReferencedFromInitializers(aClass, candidates);
    if (candidates.isEmpty()) return;

    final Set<PsiField> usedFields = new THashSet<PsiField>();
    removeReadFields(aClass, candidates, usedFields);

    if (candidates.isEmpty()) return;
    final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);

    for (PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field, implicitUsageProviders)) {
        final String message = InspectionsBundle.message("inspection.field.can.be.local.problem.descriptor");
        holder.registerProblem(field.getNameIdentifier(), message, new MyQuickFix(field));
      }
    }
  }

  private static void removeReadFields(PsiClass aClass, final Set<PsiField> candidates, final Set<PsiField> usedFields) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!candidates.isEmpty()) super.visitElement(element);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        checkCodeBlock(initializer.getBody(), candidates, usedFields);
      }
    });
  }

  private static void checkCodeBlock(final PsiCodeBlock body, final Set<PsiField> candidates, Set<PsiField> usedFields) {
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
      final List<PsiVariable> usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
      for (PsiVariable usedVariable : usedVars) {
        if (usedVariable instanceof PsiField) {
          final PsiField usedField = (PsiField)usedVariable;
          if (!usedFields.add(usedField)) {
            candidates.remove(usedField); //used in more than one code block
          }
        }
      }
      final Ref<Collection<PsiVariable>> writtenVariables = new Ref<Collection<PsiVariable>>();
      final List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      for (final PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
        final PsiElement resolved = readBeforeWrite.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField)resolved;
          if (!(field.getType() instanceof PsiPrimitiveType) || !PsiUtil.isConstantExpression(field.getInitializer()) || getWrittenVariables(controlFlow, writtenVariables).contains(field)){
            PsiElement parent = body.getParent();
            if (!(parent instanceof PsiMethod) ||
                !((PsiMethod)parent).isConstructor() ||
                field.getInitializer() == null ||
                field.hasModifierProperty(PsiModifier.STATIC) ||
                !PsiTreeUtil.isAncestor(((PsiMethod)parent).getContainingClass(), field, true)) {
              candidates.remove(field);
            }
          }
        }
      }
    }
    catch (AnalysisCanceledException e) {
      candidates.clear();
    }
  }

  private static Collection<PsiVariable> getWrittenVariables(ControlFlow controlFlow, Ref<Collection<PsiVariable>> writtenVariables) {
    if (writtenVariables.get() == null) {
      writtenVariables.set(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    }
    return writtenVariables.get();
  }

  private static void removeFieldsReferencedFromInitializers(final PsiClass aClass, final Set<PsiField> candidates) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitMethod(PsiMethod method) {
        //do not go inside method
      }

      @Override public void visitClassInitializer(PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          final PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField) {
            final PsiField field = (PsiField)resolved;
            if (aClass.equals(field.getContainingClass())) {
              candidates.remove(field);
            }
          }
        }

        super.visitReferenceExpression(expression);
      }
    });
  }

  private static boolean hasImplicitReadOrWriteUsage(final PsiField field, ImplicitUsageProvider[] implicitUsageProviders) {
    for(ImplicitUsageProvider provider: implicitUsageProviders) {
      if (provider.isImplicitRead(field) || provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  private static class MyQuickFix implements LocalQuickFix {
    private final PsiField myField;

    public MyQuickFix(final PsiField field) {
      myField = field;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.field.can.be.local.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!myField.isValid()) return; //weird. should not get here when field becomes invalid

      final Collection<PsiReference> refs = ReferencesSearch.search(myField).findAll();
      if (refs.isEmpty()) return;
      Set<PsiReference> refsSet = new HashSet<PsiReference>(refs);
      PsiCodeBlock anchorBlock = findAnchorBlock(refs);
      if (anchorBlock == null) return; //was assert, but need to fix the case when obsolete inspection highlighting is left
      if (!CodeInsightUtil.preparePsiElementsForWrite(anchorBlock)) return;
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      final String propertyName = styleManager.variableNameToPropertyName(myField.getName(), VariableKind.FIELD);
      String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      localName = RefactoringUtil.suggestUniqueVariableName(localName, anchorBlock, myField);
      PsiElement firstElement = getFirstElement(refs);
      boolean mayBeFinal = mayBeFinal(refsSet, firstElement);
      PsiElement newDeclaration = null;
      try {
        final PsiElement anchor = getAnchorElement(anchorBlock, firstElement);
        if (anchor instanceof PsiExpressionStatement &&
            ((PsiExpressionStatement) anchor).getExpression() instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression expression = (PsiAssignmentExpression) ((PsiExpressionStatement) anchor).getExpression();
          if (expression.getOperationTokenType() == JavaTokenType.EQ &&
              expression.getLExpression() instanceof PsiReferenceExpression &&
              ((PsiReference)expression.getLExpression()).isReferenceTo(myField)) {
            final PsiExpression initializer = expression.getRExpression();
            final PsiDeclarationStatement decl = elementFactory.createVariableDeclarationStatement(localName, myField.getType(), initializer);
            if (!mayBeFinal) {
              PsiUtil.setModifierProperty(((PsiModifierListOwner)decl.getDeclaredElements()[0]), PsiModifier.FINAL, false);
            }
            newDeclaration = anchor.replace(decl);
            refsSet.remove(expression.getLExpression());
            retargetReferences(elementFactory, localName, refsSet);
          }
          else {
            newDeclaration = addDeclarationWithFieldInitializerAndRetargetReferences(elementFactory, localName, anchorBlock, anchor, refsSet);
          }
        }
        else {
          newDeclaration = addDeclarationWithFieldInitializerAndRetargetReferences(elementFactory, localName, anchorBlock, anchor, refsSet);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      if (newDeclaration != null) {
        final PsiFile psiFile = myField.getContainingFile();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null && IJSwingUtilities.hasFocus(editor.getComponent())) {
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
          if (file == psiFile) {
            editor.getCaretModel().moveToOffset(newDeclaration.getTextOffset());
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        }
      }

      try {
        myField.normalizeDeclaration();
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

    }

    private static boolean mayBeFinal(Set<PsiReference> refsSet, PsiElement firstElement) {
      for (PsiReference ref : refsSet) {
        PsiElement element = ref.getElement();
        if (element == firstElement) continue;
        if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression) element)) return false;
      }
      return true;
    }

    private static void retargetReferences(final PsiElementFactory elementFactory, final String localName, final Set<PsiReference> refs)
      throws IncorrectOperationException {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)elementFactory.createExpressionFromText(localName, null);
      for (PsiReference ref : refs) {
        if (ref instanceof PsiReferenceExpression) {
          ((PsiReferenceExpression)ref).replace(refExpr);
        }
      }
    }

    private PsiElement addDeclarationWithFieldInitializerAndRetargetReferences(final PsiElementFactory elementFactory, final String localName,
                                                                               final PsiCodeBlock anchorBlock,
                                                                               final PsiElement anchor,
                                                                               final Set<PsiReference> refs)
      throws IncorrectOperationException {
      final PsiDeclarationStatement decl = elementFactory.createVariableDeclarationStatement(localName, myField.getType(), myField.getInitializer());
      final PsiElement newDeclaration = anchorBlock.addBefore(decl, anchor);

      retargetReferences(elementFactory, localName, refs);
      return newDeclaration;
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private static PsiElement getAnchorElement(final PsiCodeBlock anchorBlock, @NotNull PsiElement firstElement) {
      PsiElement element = firstElement;
      while (element != null && element.getParent() != anchorBlock) {
        element = element.getParent();
      }
      return element;
    }

    private static PsiElement getFirstElement(Collection<PsiReference> refs) {
      PsiElement firstElement = null;
      for (PsiReference reference : refs) {
        final PsiElement element = reference.getElement();
        if (firstElement == null || firstElement.getTextRange().getStartOffset() > element.getTextRange().getStartOffset()) {
          firstElement = element;
        }
      }
      return firstElement;
    }

    private static PsiCodeBlock findAnchorBlock(final Collection<PsiReference> refs) {
      PsiCodeBlock result = null;
      for (PsiReference psiReference : refs) {
        final PsiElement element = psiReference.getElement();
        PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        if (result == null || block == null) {
          result = block;
        }
        else {
          final PsiElement commonParent = PsiTreeUtil.findCommonParent(result, block);
          result = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, false);
        }
      }
      return result;
    }
  }
  public boolean runForWholeFile() {
    return true;
  }
}
