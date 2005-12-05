package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author cdr
 */
public class DefaultFileTemplateUsageInspection extends LocalInspectionTool {
  public boolean CHECK_FILE_HEADER = true;
  public boolean CHECK_TRY_CATCH_SECTION = true;
  public boolean CHECK_METHOD_BODY = true;

  public String getGroupDisplayName() {
    return GroupNames.GENERAL_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("default.file.template.display.name");
  }

  @NonNls
  public String getShortName() {
    return "DefaultFileTemplate";
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    Collection<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
    if (CHECK_METHOD_BODY) {
      MethodBodyChecker.checkMethodBody(method, manager, descriptors);
    }
    if (CHECK_TRY_CATCH_SECTION) {
      CatchBodyVisitor visitor = new CatchBodyVisitor(manager, descriptors);
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        body.accept(visitor);
      }
    }
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  static Pair<? extends PsiElement, ? extends PsiElement> getInteriorRange(PsiCodeBlock codeBlock) {
    PsiElement[] children = codeBlock.getChildren();
    if (children.length == 0) return Pair.create(codeBlock, codeBlock);
    int start;
    for (start=0; start<children.length;start++) {
      PsiElement child = children[start];
      if (child instanceof PsiWhiteSpace) continue;
      if (child instanceof PsiJavaToken && ((PsiJavaToken)child).getTokenType() == JavaTokenType.LBRACE) continue;
      break;
    }
    int end;
    for (end=children.length-1; start<end;end--) {
      PsiElement child = children[end];
      if (child instanceof PsiWhiteSpace) continue;
      if (child instanceof PsiJavaToken && ((PsiJavaToken)child).getTokenType() == JavaTokenType.RBRACE) continue;
      break;
    }
    return Pair.create(children[start], children[end]);
  }

  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    if (!CHECK_TRY_CATCH_SECTION) return null;
    CatchBodyVisitor visitor = new CatchBodyVisitor(manager, new ArrayList<ProblemDescriptor>());
    PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      initializer.accept(visitor);
    }

    return visitor.myProblemDescriptors.toArray(new ProblemDescriptor[visitor.myProblemDescriptors.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (!CHECK_FILE_HEADER) return null;
    ProblemDescriptor descriptor = FileHeaderChecker.checkFileHeader(file, manager);
    return descriptor == null ? null : new ProblemDescriptor[]{descriptor};
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    return new InspectionOptions(this).getComponent();
  }
}
