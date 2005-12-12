package com.intellij.codeInspection.java15api;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.ExtensionPoints;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class Java15APIUsageInspection extends LocalInspectionTool {
  private static THashSet<String> ourForbiddenAPI = new THashSet<String>(1500);

  static {
    initForbiddenApi();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initForbiddenApi() {
    try {
      final InputStream stream = Java15APIUsageInspection.class.getResourceAsStream("apiList.txt");
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));

      do {
        String line = reader.readLine();
        if (line == null) break;

        ourForbiddenAPI.add(line);
      } while(true);
    }
    catch (UnsupportedEncodingException e) {
      // can't be.
    }
    catch (IOException e) {
      // can't be
    }
  }

  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.1.5.display.name", "@since 1.5");
  }

  public String getShortName() {
    return "Since15";
  }


  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return checkReferencesIn(method, manager);
  }

  @Override
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    ProblemDescriptor[] result = null;
    result = merge(result, checkReferencesIn(aClass.getImplementsList(), manager));
    result = merge(result, checkReferencesIn(aClass.getExtendsList(), manager));
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      result = merge(result, checkReferencesIn(initializer, manager));
    }
    return result;
  }

  @Override
  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    return checkReferencesIn(field, manager);
  }

  private static ProblemDescriptor[] merge(ProblemDescriptor[] a, ProblemDescriptor[] b) {
    if (a == null || a.length == 0) return b;
    if (b == null || b.length == 0) return a;
    ProblemDescriptor[] res = new ProblemDescriptor[a.length + b.length];
    System.arraycopy(a, 0, res, 0, a.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }

  @Nullable
  private ProblemDescriptor[] checkReferencesIn(@Nullable PsiElement elt, InspectionManager manager) {
    if (elt == null || !isInProject(elt, manager)) return null;
    final MyVisitor visitor = new MyVisitor();
    elt.accept(visitor);
    final List<PsiElement> results = visitor.getResults();
    if (results == null) return null;
    ProblemDescriptor[] descriptors = new ProblemDescriptor[results.size()];
    for (int i = 0; i < descriptors.length; i++) {
      descriptors[i] = manager .createProblemDescriptor(results.get(i), InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"), (LocalQuickFix)null,
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

    }
    return descriptors;
  }

  private boolean isInProject(final PsiElement elt, final InspectionManager manager) {
    return PsiManager.getInstance(manager.getProject()).isInProject(elt);
  }

  @Override @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    final Object[] fileCheckingInspections = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.JAVA15_INSPECTION_TOOL).getExtensions();
    for(Object obj: fileCheckingInspections) {
      FileCheckingInspection inspection = (FileCheckingInspection) obj;
      ProblemDescriptor[] descriptors = inspection.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  private static class MyVisitor extends PsiRecursiveElementVisitor {
    private List<PsiElement> results = null;

    public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        if (isJava15APIUsage((PsiMember)resolved)) {
          registerError(reference);
        }
      }
    }

    private void registerError(PsiJavaCodeReferenceElement reference) {
      if (results == null) {
        results = new ArrayList<PsiElement>(1);
      }
      results.add(reference);
    }

    public List<PsiElement> getResults() {
      return results;
    }
  }

  public static boolean isJava15APIUsage(final PsiMember member) {
    if (member == null) return false;

    // Annotations caught by special inspection if necessary
    if (member instanceof PsiClass && ((PsiClass)member).isAnnotationType()) return false;

    if (member instanceof PsiAnonymousClass) return false;
    if (member.getContainingClass() instanceof PsiAnonymousClass) return false;
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return false;

    return ourForbiddenAPI.contains(getSignature(member)) || isJava15APIUsage(member.getContainingClass());
  }

  private static String getSignature(PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      return getSignature(member.getContainingClass()) + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      StringBuffer buf = new StringBuffer();
      buf.append(getSignature(method.getContainingClass()));
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      final PsiType[] params = method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes();
      for (PsiType type : params) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    assert false;
    return null;
  }

}
