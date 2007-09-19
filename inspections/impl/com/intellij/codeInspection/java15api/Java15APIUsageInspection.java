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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

/**
 * @author max
 */
public class Java15APIUsageInspection extends LocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "Since15";

  private static THashSet<String> ourForbidden15API = new THashSet<String>(1500);
  private static THashSet<String> ourForbidden16API = new THashSet<String>(200);

  public boolean FORBID_15_API = true;

  public boolean FORBID_16_API = true;
  private JPanel myWholePanel;
  private JCheckBox my15ApiCb;
  private JCheckBox my16ApiCb;

  static {
    initForbiddenApi("apiList.txt", ourForbidden15API);
    initForbiddenApi("api16List.txt", ourForbidden16API);
  }

  private static void initForbiddenApi(@NonNls String list, THashSet<String> set) {
    BufferedReader reader = null;
    try {
      final InputStream stream = Java15APIUsageInspection.class.getResourceAsStream(list);
      reader = new BufferedReader(new InputStreamReader(stream, CharsetToolkit.UTF8_CHARSET));

      do {
        String line = reader.readLine();
        if (line == null) break;

        set.add(line);
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

  @Nullable
  public JComponent createOptionsPanel() {
    my15ApiCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        FORBID_15_API = my15ApiCb.isSelected();
      }
    });
    my15ApiCb.setSelected(FORBID_15_API);
    my16ApiCb.setSelected(FORBID_16_API);
    my16ApiCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        FORBID_16_API = my16ApiCb.isSelected();
      }
    });
    return myWholePanel;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.1.5.display.name", "@since 1.5(1.6)");
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }


  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  private static boolean isInProject(final PsiElement elt) {
    return elt.getManager().isInProject(elt);
  }

  @Override @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
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

  private class MyVisitor extends PsiElementVisitor {
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
        if (isJava15ApiUsage((PsiMember)resolved)) {
          register15Error(reference);
        } else if (isJava16ApiUsage(((PsiMember)resolved))) {
          register16Error(reference);
        }
      }
    }

    public void visitNewExpression(final PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor instanceof PsiCompiledElement) {
        if (isJava15ApiUsage(constructor)) {
          register15Error(expression.getClassReference());
        } else if (isJava16ApiUsage(constructor)) {
          register16Error(expression.getClassReference());
        }
      }
    }

    private void registerError(PsiJavaCodeReferenceElement reference, @NonNls String api) {
      if (isInProject(reference)) {
        myHolder.registerProblem(reference, InspectionsBundle.message("inspection.1.5.problem.descriptor", api));
      }
    }

    private void register15Error(PsiJavaCodeReferenceElement referenceElement) {
      registerError(referenceElement, "@since 1.5");
    }

    private void register16Error(PsiJavaCodeReferenceElement referenceElement) {
      registerError(referenceElement, "@since 1.6");
    }
  }

  private static boolean isForbiddenApiUsage(final PsiMember member, boolean is15ApiCheck) {
    if (member == null) return false;

    // Annotations caught by special inspection if necessary
    if (member instanceof PsiClass && ((PsiClass)member).isAnnotationType()) return false;

    if (member instanceof PsiAnonymousClass) return false;
    if (member.getContainingClass() instanceof PsiAnonymousClass) return false;
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return false;

    return (is15ApiCheck && ourForbidden15API.contains(getSignature(member))) ||
           (!is15ApiCheck && ourForbidden16API.contains(getSignature(member))) ||
           isForbiddenApiUsage(member.getContainingClass(), is15ApiCheck);

  }

  public boolean isJava15ApiUsage(final PsiMember member) {
    return FORBID_15_API && isForbiddenApiUsage(member, true);
  }

  public boolean isJava16ApiUsage(final PsiMember member) {
    return (FORBID_15_API || FORBID_16_API) && isForbiddenApiUsage(member, false);
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
