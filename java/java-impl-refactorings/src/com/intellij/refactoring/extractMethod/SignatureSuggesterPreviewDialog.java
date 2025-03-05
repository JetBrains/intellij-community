// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SignatureSuggesterPreviewDialog extends DialogWrapper {
  private static final @NonNls String DIFF_PLACE = "ExtractSignature";

  private final PsiMethod myOldMethod;
  private final PsiMethod myNewMethod;
  private final PsiMethodCallExpression myOldCall;
  private final PsiMethodCallExpression myNewCall;
  private final int myParametrizedDuplicatesNumber;
  private final int myExactDuplicatesNumber;

  public SignatureSuggesterPreviewDialog(@NotNull PsiMethod oldMethod,
                                         @NotNull PsiMethod newMethod,
                                         @NotNull PsiMethodCallExpression oldMethodCall,
                                         @NotNull PsiMethodCallExpression newMethodCall,
                                         int exactDuplicatesNumber,
                                         int parametrizedDuplicatesNumber) {
    super(oldMethod.getProject());
    myOldMethod = oldMethod;
    myNewMethod = newMethod;
    myOldCall = oldMethodCall;
    myNewCall = newMethodCall;
    myParametrizedDuplicatesNumber = parametrizedDuplicatesNumber;
    myExactDuplicatesNumber = exactDuplicatesNumber;
    setTitle(JavaRefactoringBundle.message("extract.parameters.to.replace.duplicates"));
    setOKButtonText(JavaRefactoringBundle.message("accept.signature.change"));
    setCancelButtonText(JavaRefactoringBundle.message("keep.original.signature"));
    init();
  }

  @Override
  protected @Nullable JComponent createNorthPanel() {
    return new JLabel(JavaRefactoringBundle.message("no.exact.method.duplicates.were.found", myExactDuplicatesNumber, myParametrizedDuplicatesNumber));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    final Project project = myOldMethod.getProject();
    final VirtualFile file = PsiUtilCore.getVirtualFile(myOldMethod);

    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    @Nls String methodCallPrefix = JavaRefactoringBundle.message("suggest.signature.preview.method.call.prefix");
    DocumentContent oldContent = contentFactory.create(myOldMethod.getText() + "\n\n\n" + methodCallPrefix + "\n " + myOldCall.getText(), file);
    DocumentContent newContent = contentFactory.create(myNewMethod.getText() + "\n\n\n" + methodCallPrefix + "\n " + myNewCall.getText(), file);
    SimpleDiffRequest request = new SimpleDiffRequest(null, oldContent, newContent,
                                                      JavaRefactoringBundle.message("suggest.signature.preview.title.before"),
                                                      JavaRefactoringBundle.message("suggest.signature.preview.after.title"));

    DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
    diffPanel.putContextHints(DiffUserDataKeys.PLACE, DIFF_PLACE);
    diffPanel.setRequest(request);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(diffPanel.getComponent(), BorderLayout.CENTER);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(5)));
    return panel;
  }
}
