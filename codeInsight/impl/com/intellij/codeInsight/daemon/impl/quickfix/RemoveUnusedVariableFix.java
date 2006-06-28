package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RemoveUnusedVariableFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix");
  private final PsiVariable myVariable;
  private static final @NonNls String JAVA_LANG_PCKG = "java.lang";
  private static final @NonNls String JAVA_IO_PCKG = "java.io";

  public RemoveUnusedVariableFix(PsiVariable variable) {
    myVariable = variable;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myVariable instanceof PsiField ? "remove.unused.field" : "remove.unused.variable",
                                  myVariable.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.variable.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
      myVariable != null
      && myVariable.isValid()
      && myVariable.getManager().isInProject(myVariable)
      ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;
    removeVariableAndReferencingStatements(editor);
  }

  private static void deleteReferences(PsiVariable variable, List<PsiElement> references, int mode) throws IncorrectOperationException {
    for (PsiElement expression : references) {
      processUsage(expression, variable, null, mode);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<PsiElement> references) {
    context.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private void removeVariableAndReferencingStatements(Editor editor) {
    final List<PsiElement> references = new ArrayList<PsiElement>();
    final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
    final boolean[] canCopeWithSideEffects = {true};
    try {
      PsiElement context = myVariable instanceof PsiField ? ((PsiField)myVariable).getContainingClass() : PsiUtil.getVariableCodeBlock(myVariable, null);
      collectReferences(context, myVariable, references);
      // do not forget to delete variable declaration
      references.add(myVariable);
      // check for side effects
      for (PsiElement element : references) {
        canCopeWithSideEffects[0] &= processUsage(element, myVariable, sideEffects, SideEffectWarningDialog.CANCEL);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    final int deleteMode = showSideEffectsWarning(sideEffects, myVariable, editor, canCopeWithSideEffects[0]);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          deleteReferences(myVariable, references, deleteMode);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public static int showSideEffectsWarning(List<PsiElement> sideEffects,
                                           PsiVariable variable,
                                           Editor editor,
                                           boolean canCopeWithSideEffects,
                                           @NonNls String beforeText,
                                           @NonNls String afterText) {
    if (sideEffects.isEmpty()) return SideEffectWarningDialog.DELETE_ALL;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return canCopeWithSideEffects
             ? SideEffectWarningDialog.MAKE_STATEMENT
             : SideEffectWarningDialog.DELETE_ALL;
    }
    Project project = variable.getProject();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    PsiElement[] elements = sideEffects.toArray(new PsiElement[sideEffects.size()]);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, true, null);

    SideEffectWarningDialog dialog = new SideEffectWarningDialog(project, false, variable, beforeText, afterText, canCopeWithSideEffects);
    dialog.show();
    return dialog.getExitCode();
  }

  private static int showSideEffectsWarning(List<PsiElement> sideEffects,
                                            PsiVariable variable,
                                            Editor editor,
                                            boolean canCopeWithSideEffects) {
    String text = !sideEffects.isEmpty() ? sideEffects.get(0).getText() : "";
    return showSideEffectsWarning(sideEffects, variable, editor, canCopeWithSideEffects, text, text);
  }

  /**
   *
   * @param element
   * @param variable
   * @param sideEffects if null, delete usages, otherwise collect side effects
   * @return true if there are at least one unrecoverable side effect found
   * @throws IncorrectOperationException
   */
  private static boolean processUsage(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects, int deleteMode)
    throws IncorrectOperationException {
    if (!element.isValid()) return true;
    PsiElementFactory factory = variable.getManager().getElementFactory();
    while (element != null) {
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
        PsiExpression lExpression = expression.getLExpression();
        // there should not be read access to the variable, otherwise it is not unused
        LOG.assertTrue(
          lExpression instanceof PsiReferenceExpression &&
          variable == ((PsiReferenceExpression)lExpression).resolve());
        PsiExpression rExpression = expression.getRExpression();
        rExpression = PsiUtil.deparenthesizeExpression(rExpression);
        if (rExpression == null) return true;
        // replace assignment with expression and resimplify
        boolean sideEffectFound = checkSideEffects(rExpression, variable, sideEffects);
        if (!(element.getParent() instanceof PsiExpressionStatement) || PsiUtil.isStatement(rExpression)) {
          if (deleteMode == SideEffectWarningDialog.MAKE_STATEMENT ||
              deleteMode == SideEffectWarningDialog.DELETE_ALL && !(element.getParent() instanceof PsiExpressionStatement)) {
            element = replaceElementWithExpression(rExpression, factory, element);
            while (element.getParent() instanceof PsiParenthesizedExpression) {
              element = element.getParent().replace(element);
            }
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == SideEffectWarningDialog.DELETE_ALL) {
            deleteWholeStatement(element, factory);
          }
          return true;
        }
        else {
          if (deleteMode != SideEffectWarningDialog.CANCEL) {
            deleteWholeStatement(element, factory);
          }
          return !sideEffectFound;
        }
      }
      else if (element instanceof PsiExpressionStatement && deleteMode != SideEffectWarningDialog.CANCEL) {
        element.delete();
        break;
      }
      else if (element instanceof PsiVariable && element == variable) {
        PsiExpression expression = variable.getInitializer();
        if (expression != null) {
          expression = PsiUtil.deparenthesizeExpression(expression);
        }
        boolean sideEffectsFound = checkSideEffects(expression, variable, sideEffects);
        if (expression != null && PsiUtil.isStatement(expression) && variable instanceof PsiLocalVariable
            &&
            !(variable.getParent() instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length > 1)) {
          if (deleteMode == SideEffectWarningDialog.MAKE_STATEMENT) {
            element = element.replace(createStatementIfNeeded(expression, factory, element));
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == SideEffectWarningDialog.DELETE_ALL) {
            element.delete();
          }
          return true;
        }
        else {
          if (deleteMode != SideEffectWarningDialog.CANCEL) {
            if (element instanceof PsiField) {
              ((PsiField)element).normalizeDeclaration();
            }
            element.delete();
          }
          return !sideEffectsFound;
        }
      }
      element = element.getParent();
    }
    return true;
  }

  private static void deleteWholeStatement(PsiElement element, PsiElementFactory factory)
    throws IncorrectOperationException {
    // just delete it altogether
    if (element.getParent() instanceof PsiExpressionStatement) {
      PsiExpressionStatement parent = (PsiExpressionStatement)element.getParent();
      if (parent.getParent() instanceof PsiCodeBlock) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        parent.replace(createStatementIfNeeded(null, factory, element));
      }
    }
    else {
      element.delete();
    }
  }

  private static PsiElement createStatementIfNeeded(PsiExpression expression,
                                                    PsiElementFactory factory,
                                                    PsiElement element) throws IncorrectOperationException {
    // if element used in expression, subexpression will do
    if (!(element.getParent() instanceof PsiExpressionStatement) &&
        !(element.getParent() instanceof PsiDeclarationStatement)) {
      return expression;
    }
    return factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
  }

  private static PsiElement replaceElementWithExpression(PsiExpression expression,
                                                         PsiElementFactory factory,
                                                         PsiElement element) throws IncorrectOperationException {
    PsiElement elementToReplace = element;
    PsiElement expressionToReplaceWith = expression;
    if (element.getParent() instanceof PsiExpressionStatement) {
      elementToReplace = element.getParent();
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    else if (element.getParent() instanceof PsiDeclarationStatement) {
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    return elementToReplace.replace(expressionToReplaceWith);
  }

    public static boolean checkSideEffects(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects) {
    if (sideEffects == null || element == null) return false;
    if (element instanceof PsiMethodCallExpression) {
      sideEffects.add(element);
      return true;
    }
    if (element instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)element;
      if (newExpression.getArrayDimensions().length == 0
          && newExpression.getArrayInitializer() == null
          && !isSideEffectFreeConstructor(newExpression)) {
        sideEffects.add(element);
        return true;
      }
    }
    if (element instanceof PsiAssignmentExpression
        && !(((PsiAssignmentExpression)element).getLExpression() instanceof PsiReferenceExpression
             && ((PsiReferenceExpression)((PsiAssignmentExpression)element).getLExpression()).resolve() == variable)) {
      sideEffects.add(element);
      return true;
    }
    PsiElement[] children = element.getChildren();

      for (PsiElement child : children) {
        checkSideEffects(child, variable, sideEffects);
      }
    return !sideEffects.isEmpty();
  }

  private static final Set<String> ourSideEffectFreeClasses = new THashSet<String>();
  static {
    ourSideEffectFreeClasses.add(Object.class.getName());
    ourSideEffectFreeClasses.add(Short.class.getName());
    ourSideEffectFreeClasses.add(Character.class.getName());
    ourSideEffectFreeClasses.add(Byte.class.getName());
    ourSideEffectFreeClasses.add(Integer.class.getName());
    ourSideEffectFreeClasses.add(Long.class.getName());
    ourSideEffectFreeClasses.add(Float.class.getName());
    ourSideEffectFreeClasses.add(Double.class.getName());
    ourSideEffectFreeClasses.add(String.class.getName());
    ourSideEffectFreeClasses.add(StringBuffer.class.getName());
    ourSideEffectFreeClasses.add(Boolean.class.getName());

    ourSideEffectFreeClasses.add(ArrayList.class.getName());
    ourSideEffectFreeClasses.add(Date.class.getName());
    ourSideEffectFreeClasses.add(HashMap.class.getName());
    ourSideEffectFreeClasses.add(HashSet.class.getName());
    ourSideEffectFreeClasses.add(Hashtable.class.getName());
    ourSideEffectFreeClasses.add(LinkedHashMap.class.getName());
    ourSideEffectFreeClasses.add(LinkedHashSet.class.getName());
    ourSideEffectFreeClasses.add(LinkedList.class.getName());
    ourSideEffectFreeClasses.add(Stack.class.getName());
    ourSideEffectFreeClasses.add(TreeMap.class.getName());
    ourSideEffectFreeClasses.add(TreeSet.class.getName());
    ourSideEffectFreeClasses.add(Vector.class.getName());
    ourSideEffectFreeClasses.add(WeakHashMap.class.getName());
  }

  private static boolean isSideEffectFreeConstructor(PsiNewExpression newExpression) {
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    PsiClass aClass = classReference == null ? null : (PsiClass)classReference.resolve();
    String qualifiedName = aClass == null ? null : aClass.getQualifiedName();
    if (qualifiedName == null) return false;
    if (ourSideEffectFreeClasses.contains(qualifiedName)) return true;

    PsiFile file = aClass.getContainingFile();
    PsiDirectory directory = file.getContainingDirectory();
    PsiPackage classPackage = directory.getPackage();
    String packageName = classPackage.getQualifiedName();

    // all Throwable descendants from java.lang are side effects free
    if (JAVA_LANG_PCKG.equals(packageName) || JAVA_IO_PCKG.equals(packageName)) {
      PsiClass throwableClass = aClass.getManager().findClass("java.lang.Throwable", aClass.getResolveScope());
      if (throwableClass != null && InheritanceUtil.isInheritorOrSelf(aClass, throwableClass, true)) {
        return true;
      }
    }
    return false;
  }

  public boolean startInWriteAction() {
    return false;
  }


}
