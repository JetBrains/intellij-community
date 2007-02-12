package com.intellij.codeInspection.java15api;

import com.intellij.ExtensionPoints;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.*;

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
    BufferedReader reader = null;
    try {
      final InputStream stream = Java15APIUsageInspection.class.getResourceAsStream("apiList.txt");
      reader = new BufferedReader(new InputStreamReader(stream, CharsetToolkit.UTF8_CHARSET));

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
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          // Will not happen
        }
      }
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

  @NotNull
  public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  private static boolean isInProject(final PsiElement elt) {
    return elt.getManager().isInProject(elt);
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

  private static class MyVisitor extends PsiElementVisitor {
    private ProblemsHolder myHolder;

    public MyVisitor(final ProblemsHolder holder) {
      myHolder = holder;
    }

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
      if (isInProject(reference)) {
        myHolder.registerProblem(reference, InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"));
      }
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
