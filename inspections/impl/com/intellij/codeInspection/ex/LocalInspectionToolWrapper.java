package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public final class LocalInspectionToolWrapper extends DescriptorProviderInspection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.LocalInspectionToolWrapper");

  private LocalInspectionTool myTool;

  public LocalInspectionToolWrapper(LocalInspectionTool tool) {
    myTool = tool;
  }

  public LocalInspectionTool getTool() {
    return myTool;
  }

  public void processFile(PsiFile file, final boolean filterSuppressed) {
    final ProblemsHolder holder = new ProblemsHolder(getManager());
    final PsiElementVisitor customVisitor = myTool.buildVisitor(holder, false);

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        if (customVisitor != null) {
          element.accept(customVisitor);
        }

        super.visitElement(element);
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override
      public void visitJspFile(JspFile file) {
        final PsiElement[] roots = file.getPsiRoots();
        for (PsiElement root : roots) {
          visitElement(root);
        }
      }

      public void visitField(PsiField field) {
        super.visitField(field);
        if (!filterSuppressed || InspectionManagerEx.isToCheckMember(field, myTool.getID())) {
          ProblemDescriptor[] problemDescriptions = myTool.checkField(field, getManager(), false);
          addProblemDescriptors(field, problemDescriptions, filterSuppressed);
        }
      }

      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        if (!filterSuppressed || InspectionManagerEx.isToCheckMember(aClass, myTool.getID()) && !(aClass instanceof PsiTypeParameter)) {
          ProblemDescriptor[] problemDescriptions = myTool.checkClass(aClass, getManager(), false);
          addProblemDescriptors(aClass, problemDescriptions, filterSuppressed);
        }
      }


      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        if (!filterSuppressed || InspectionManagerEx.isToCheckMember(method, myTool.getID())) {
          ProblemDescriptor[] problemDescriptions = myTool.checkMethod(method, getManager(), false);
          addProblemDescriptors(method, problemDescriptions, filterSuppressed);
        }
      }

      public void visitFile(PsiFile file) {
        super.visitFile(file);
        ProblemDescriptor[] problemDescriptions = myTool.checkFile(file, getManager(), false);
        addProblemDescriptors(file, problemDescriptions, filterSuppressed);
      }
    });

    addProblemDescriptors(holder.getResults(), filterSuppressed);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return JobDescriptor.EMPTY_ARRAY;
  }

  private void addProblemDescriptors(List<ProblemDescriptor> descriptors, final boolean filterSuppressed) {
    if (descriptors == null || descriptors.isEmpty()) return;

    Map<RefElement, List<ProblemDescriptor>> problems = new HashMap<RefElement, List<ProblemDescriptor>>();
    RefManager refManager = getManager().getRefManager();
    for (ProblemDescriptor descriptor : descriptors) {
      final PsiElement elt = descriptor.getPsiElement();
      if (filterSuppressed && InspectionManagerEx.inspectionResultSuppressed(descriptor.getPsiElement(), myTool.getID())) continue;

      final PsiNamedElement problemElement =
        PsiTreeUtil.getNonStrictParentOfType(elt, PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class);

      RefElement refElement = refManager.getReference(problemElement);
      List<ProblemDescriptor> elementProblems = problems.get(refElement);
      if (elementProblems == null) {
        elementProblems = new ArrayList<ProblemDescriptor>();
        problems.put(refElement, elementProblems);
      }
      elementProblems.add(descriptor);
    }

    for (Map.Entry<RefElement, List<ProblemDescriptor>> entry : problems.entrySet()) {
      final List<ProblemDescriptor> problemDescriptors = entry.getValue();
      addProblemElement(entry.getKey(),
                        problemDescriptors.toArray(new CommonProblemDescriptor[problemDescriptors.size()]));
    }
  }

  private ProblemDescriptor[] filterUnsuppressedProblemDescriptions(ProblemDescriptor[] problemDescriptions) {
    Set<ProblemDescriptor> set = null;
    for (ProblemDescriptor description : problemDescriptions) {
      if (InspectionManagerEx.inspectionResultSuppressed(description.getPsiElement(), myTool.getID())) {
        if (set == null) set = new LinkedHashSet<ProblemDescriptor>(Arrays.asList(problemDescriptions));
        set.remove(description);
      }
    }
    return set == null ? problemDescriptions : set.toArray(new ProblemDescriptor[set.size()]);
  }

  private void addProblemDescriptors(PsiElement element, ProblemDescriptor[] problemDescriptions, final boolean filterSuppressed) {
    if (problemDescriptions != null) {
      if (filterSuppressed) {
        problemDescriptions = filterUnsuppressedProblemDescriptions(problemDescriptions);
      }
      if (problemDescriptions.length != 0) {
        RefManager refManager = getManager().getRefManager();
        RefElement refElement = refManager.getReference(element);
        if (refElement != null) {
          addProblemElement(refElement, problemDescriptions);
        }
      }
    }
  }

  public void runInspection(AnalysisScope scope) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      public void visitFile(PsiFile file) {
        processFile(file, true);
      }
    });
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public String getDisplayName() {
    return myTool.getDisplayName();
  }

  public String getGroupDisplayName() {
    return myTool.getGroupDisplayName();
  }

  public String getShortName() {
    return myTool.getShortName();
  }

  public boolean isEnabledByDefault() {
    return myTool.isEnabledByDefault();
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return myTool.getDefaultLevel();
  }

  public void readSettings(Element element) throws InvalidDataException {
    myTool.readSettings(element);
  }

  public void writeSettings(Element element) throws WriteExternalException {
    myTool.writeSettings(element);
  }

  public JComponent createOptionsPanel() {
    return myTool.createOptionsPanel();    
  }
}
