package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class FieldCanBeLocalInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection");

  @NonNls public static final String SHORT_NAME = "FieldCanBeLocal";

  public String getGroupDisplayName() {
    return GroupNames.CLASSLAYOUT_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.field.can.be.local.display.name");
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  public ProblemDescriptor[] checkClass(final PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    if (aClass.isInterface()) return null;
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(aClass);
    if (topLevelClass == null) return null;
    final PsiField[] fields = aClass.getFields();
    final Set<PsiField> candidates = new LinkedHashSet<PsiField>();
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        candidates.add(field);
      }
    }

    topLevelClass.accept(new PsiRecursiveElementVisitor() {
      public void visitMethod(PsiMethod method) {
        //do not go inside method
      }

      public void visitClassInitializer(PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier == null || (qualifier instanceof PsiThisExpression && ((PsiThisExpression)qualifier).getQualifier() == null)) {
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

    final Set<PsiField> usedFields = new HashSet<PsiField>();
    topLevelClass.accept(new PsiRecursiveElementVisitor() {

      public void visitElement(PsiElement element) {
        if (candidates.size() > 0) super.visitElement(element);
      }

      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates);
        }
      }

      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        checkCodeBlock(initializer.getBody(), candidates);
      }

      private void checkCodeBlock(final PsiCodeBlock body, final Set<PsiField> candidates) {
        try {
          final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
          final PsiVariable[] usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
          for (PsiVariable usedVariable : usedVars) {
            if (usedVariable instanceof PsiField) {
              final PsiField usedField = (PsiField)usedVariable;
              if (usedFields.contains(usedField)) {
                candidates.remove(usedField); //used in more than one code block
              } else {
                usedFields.add(usedField);
              }
            }
          }
          final List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
          for (final PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
            final PsiElement resolved = readBeforeWrite.resolve();
            if (resolved instanceof PsiField) {
              final PsiField field = (PsiField)resolved;
              candidates.remove(field);
            }
          }
        }
        catch (AnalysisCanceledException e) {
          candidates.clear();
        }
      }
    });

    if (candidates.isEmpty()) return null;
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiField field : candidates) {
      if (usedFields.contains(field)) {
        final String message = InspectionsBundle.message("inspection.field.can.be.local.problem.descriptor");
        result.add(manager.createProblemDescriptor(field.getNameIdentifier(), message, new MyQuickFix(field),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  private static class MyQuickFix implements LocalQuickFix {
    private PsiField myField;

    public MyQuickFix(final PsiField field) {
      myField = field;
    }

    public String getName() {
      return InspectionsBundle.message("inspection.field.can.be.local.quickfix");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      if (!myField.isValid()) return; //weird. should not get here when field becomes invalid

      PsiManager manager = PsiManager.getInstance(project);
      final Collection<PsiReference> refs = ReferencesSearch.search(myField).findAll();
      LOG.assertTrue(refs.size() > 0);
      Set<PsiReference> refsSet = new HashSet<PsiReference>(refs);
      PsiCodeBlock anchorBlock = findAnchorBlock(refs);
      if (anchorBlock == null) return; //was assert, but need to fix the case when obsolete inspection highlighting is left
      final PsiElementFactory elementFactory = manager.getElementFactory();
      final CodeStyleManager styleManager = manager.getCodeStyleManager();
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
          if (expression.getLExpression() instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression) expression.getLExpression()).isReferenceTo(myField)) {
            final PsiExpression initializer = expression.getRExpression();
            final PsiDeclarationStatement decl = elementFactory.createVariableDeclarationStatement(localName, myField.getType(), initializer);
            if (!mayBeFinal) {
              ((PsiVariable) decl.getDeclaredElements()[0]).getModifierList().setModifierProperty(PsiModifier.FINAL, false);
            }
            newDeclaration = anchor.replace(decl);
            refsSet.remove(expression.getLExpression());
            retargetReferences(elementFactory, localName, refsSet);
          } else {
            newDeclaration = addDeclarationWithoutInitializerAndRetargetReferences(elementFactory, localName, anchorBlock, anchor, refsSet);
          }
        } else {
          newDeclaration = addDeclarationWithoutInitializerAndRetargetReferences(elementFactory, localName, anchorBlock, anchor, refsSet);
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

    private PsiElement addDeclarationWithoutInitializerAndRetargetReferences(final PsiElementFactory elementFactory,
                                                                             final String localName,
                                                                             final PsiCodeBlock anchorBlock, final PsiElement anchor,
                                                                             final Set<PsiReference> refs)
      throws IncorrectOperationException {
      final PsiDeclarationStatement decl = elementFactory.createVariableDeclarationStatement(localName, myField.getType(), null);
      final PsiElement newDeclaration = anchorBlock.addBefore(decl, anchor);

      retargetReferences(elementFactory, localName, refs);
      return newDeclaration;
    }

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
        if (result == null) {
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
}
