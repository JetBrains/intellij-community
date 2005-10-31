package com.intellij.codeInspection;

import org.jetbrains.annotations.Nullable;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.10.2005
 * Time: 13:48:35
 * To change this template use File | Settings | File Templates.
 */
public interface FileCheckingInspection {
  @Nullable
  ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly);
}
