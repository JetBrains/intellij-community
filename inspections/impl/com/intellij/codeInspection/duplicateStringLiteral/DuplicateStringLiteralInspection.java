package com.intellij.codeInspection.duplicateStringLiteral;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurences.BaseOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceFilter;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jdom.Element;

import javax.swing.*;
import java.util.*;

public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.DuplicateStringLiteralInspection");
  private JTextField myMinStringLengthField;
  public int MIN_STRING_LENGTH = 5;
  private JLabel myMinStringLengthLabel;
  private JPanel myPanel;

  public DuplicateStringLiteralInspection() {
    myMinStringLengthLabel.setLabelFor(myMinStringLengthField);
    myMinStringLengthField.setText(Integer.toString(MIN_STRING_LENGTH));
  }

  private List<ProblemDescriptor> visitExpressionsUnder(PsiElement element, final InspectionManager manager, final boolean onTheFly) {
    if (element == null) return Collections.emptyList();
    final List<ProblemDescriptor> allProblems = new ArrayList<ProblemDescriptor>();
    element.acceptChildren(new PsiRecursiveElementVisitor() {
      public void visitClass(PsiClass aClass) {
        // prevent double class checking
      }

      public void visitAnnotation(PsiAnnotation annotation) {
        //prevent from @SuppressWarnings
        if (!"java.lang.SuppressWarnings".equals(annotation.getQualifiedName())){
          super.visitAnnotation(annotation);
        }
      }

      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkStringLiteralExpression(expression, manager, allProblems, onTheFly);
      }

    });
    return allProblems;
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    final List<ProblemDescriptor> allProblems = visitExpressionsUnder(aClass, manager, isOnTheFly);
    return allProblems.size() == 0 ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicates.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  public String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  private void checkStringLiteralExpression(final PsiLiteralExpression originalExpression,
                                            InspectionManager manager,
                                            final List<ProblemDescriptor> allProblems, final boolean isOnTheFly) {
    if (!(originalExpression.getValue() instanceof String)) return;
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(originalExpression.getProject());
    final String stringToFind = (String)originalExpression.getValue();
    final PsiSearchHelper searchHelper = originalExpression.getManager().getSearchHelper();
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.size() == 0) return;
    // put longer strings first
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });

    Set<PsiFile> resultFiles = null;
    for (String word : words) {
      if (word.length() >= MIN_STRING_LENGTH) {
        final Set<PsiFile> files = new THashSet<PsiFile>();
        searchHelper.processAllFilesWithWordInLiterals(word, scope, new CommonProcessors.CollectProcessor<PsiFile>(files));
        if (resultFiles == null) {
          resultFiles = files;
        }
        else {
          resultFiles.retainAll(files);
        }
        if (resultFiles.size() == 0) return;
      }
    }
    if (resultFiles == null || resultFiles.size() == 0) return;
    final List<PsiExpression> foundExpr = new ArrayList<PsiExpression>();
    for (PsiFile file : resultFiles) {
      char[] text = file.textToCharArray();
      StringSearcher searcher = new StringSearcher(stringToFind);
      for (int offset = LowLevelSearchUtil.searchWord(text, 0, text.length, searcher);
           offset >= 0;
           offset = LowLevelSearchUtil.searchWord(text, offset + searcher.getPattern().length(), text.length, searcher)
        ) {
        PsiElement element = file.findElementAt(offset);
        if (!(element.getParent() instanceof PsiLiteralExpression)) continue;
        PsiLiteralExpression expression = (PsiLiteralExpression)element.getParent();
        if (expression != originalExpression && Comparing.equal(stringToFind, expression.getValue())) {
          foundExpr.add(expression);
        }
      }
    }
    if (foundExpr.size() == 0) return;
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiElement aClass : foundExpr) {
      do {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      while (aClass != null && ((PsiClass)aClass).getQualifiedName() == null);
      if (aClass != null) {
        classes.add((PsiClass)aClass);
      }
    }
    if (classes.size() == 0) return;

    List<PsiClass> tenClassesMost = Arrays.asList(classes.toArray(new PsiClass[classes.size()]));
    if (tenClassesMost.size() > 10) {
      tenClassesMost = tenClassesMost.subList(0, 10);
    }

    String classList;
    if (isOnTheFly) {
      classList = StringUtil.join(tenClassesMost, new Function<PsiClass, String>() {
        public String fun(final PsiClass aClass) {
          final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
          //noinspection HardCodedStringLiteral
          return "&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'" +
                 (thisFile ? " " + InspectionsBundle.message("inspection.duplicates.message.in.this.file") : "");
        }
      }, ", <br>");

    }
    else {
      classList = StringUtil.join(tenClassesMost, new Function<PsiClass, String>() {
        public String fun(final PsiClass aClass) {
          final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
          return "'" + aClass.getQualifiedName() + "'";
        }
      }, ", ");
    }

    if (classes.size() > tenClassesMost.size()) {
      //noinspection HardCodedStringLiteral
      classList += "<br>" + InspectionsBundle.message("inspection.duplicates.message.more", (classes.size() - 10));
    }

    String msg = InspectionsBundle.message("inspection.duplicates.message", classList);

    Collection<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
    final LocalQuickFix introduceConstFix = createIntroduceConstFix(foundExpr, originalExpression);
    fixes.add(introduceConstFix);
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(new LocalQuickFix[fixes.size()]);
    ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(originalExpression, msg, array, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    allProblems.add(problemDescriptor);
  }

  private static void createReplaceFixes(final List<PsiExpression> foundExpr, final PsiLiteralExpression originalExpression,
                                         final Collection<LocalQuickFix> fixes) {
    Set<PsiField> constants = new THashSet<PsiField>();
    for (Iterator<PsiExpression> iterator = foundExpr.iterator(); iterator.hasNext();) {
      PsiExpression expression1 = iterator.next();
      if (expression1.getParent() instanceof PsiField) {
        final PsiField field = (PsiField)expression1.getParent();
        if (field.getInitializer() == expression1 && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
          constants.add(field);
          iterator.remove();
        }
      }
    }
    for (final PsiField constant : constants) {
      final PsiClass containingClass = constant.getContainingClass();
      if (containingClass == null) continue;
      boolean isAccessible = PsiManager.getInstance(constant.getProject()).getResolveHelper() .isAccessible(constant, originalExpression,
                                                                                                            containingClass);
      if (!isAccessible && containingClass.getQualifiedName() == null) {
        continue;
      }
      final LocalQuickFix replaceQuickFix = new LocalQuickFix() {
        public String getName() {
          return InspectionsBundle.message("inspection.duplicates.replace.quickfix", PsiFormatUtil
            .formatVariable(constant, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME | PsiFormatUtil.SHOW_NAME,
                            PsiSubstitutor.EMPTY));
        }

        public void applyFix(final Project project, ProblemDescriptor descriptor) {
          if (!CodeInsightUtil.prepareFileForWrite(originalExpression.getContainingFile())) return;
          try {
            final PsiReferenceExpression reference = createReferenceTo(constant, originalExpression);
            originalExpression.replace(reference);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }

        public String getFamilyName() {
          return InspectionsBundle.message("inspection.duplicates.replace.family.quickfix");
        }
      };
      fixes.add(replaceQuickFix);
    }
  }

  private static LocalQuickFix createIntroduceConstFix(final List<PsiExpression> foundExpr, final PsiLiteralExpression originalExpression) {
    final PsiExpression[] expressions = foundExpr.toArray(new PsiExpression[foundExpr.size() + 1]);
    expressions[foundExpr.size()] = originalExpression;

    final LocalQuickFix introduceConstFix = new LocalQuickFix() {
      public String getName() {
        return IntroduceConstantHandler.REFACTORING_NAME;
      }

      public void applyFix(final Project project, ProblemDescriptor descriptor) {
        final IntroduceConstantHandler handler = new IntroduceConstantHandler() {
          protected OccurenceManager createOccurenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
            final OccurenceFilter filter = new OccurenceFilter() {
              public boolean isOK(PsiExpression occurence) {
                return true;
              }
            };
            return new BaseOccurenceManager(filter) {
              protected PsiExpression[] defaultOccurences() {
                return expressions;
              }

              protected PsiExpression[] findOccurences() {
                return expressions;
              }
            };
          }
        };
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            handler.invoke(project, expressions);
          }
        });
      }

      public String getFamilyName() {
        return getName();
      }
    };
    return introduceConstFix;
  }

  private static PsiReferenceExpression createReferenceTo(final PsiField constant, final PsiLiteralExpression context) throws IncorrectOperationException {
    PsiReferenceExpression reference = (PsiReferenceExpression)constant.getManager().getElementFactory().createExpressionFromText(constant.getName(), context);
    if (reference.isReferenceTo(constant)) return reference;
    //noinspection HardCodedStringLiteral
    reference = (PsiReferenceExpression)constant.getManager().getElementFactory().createExpressionFromText("XXX."+constant.getName(), null);
    final PsiReferenceExpression classQualifier = (PsiReferenceExpression)reference.getQualifierExpression();
    classQualifier.bindToElement(constant.getContainingClass());

    if (reference.isReferenceTo(constant)) return reference;
    return null;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  public JComponent createOptionsPanel() {
    return myPanel;
  }

  public void readSettings(Element node) throws InvalidDataException {
    super.readSettings(node);
    myMinStringLengthField.setText(Integer.toString(MIN_STRING_LENGTH));
  }

  public void writeSettings(Element node) throws WriteExternalException {
    try {
      MIN_STRING_LENGTH = Integer.parseInt(myMinStringLengthField.getText());
    }
    catch (NumberFormatException e) {
    }
    super.writeSettings(node);
  }
}
