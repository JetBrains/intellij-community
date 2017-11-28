/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SignatureSuggesterPreviewDialog extends DialogWrapper {
  private final PsiMethod myOldMethod;
  private final PsiMethod myNewMethod;
  private final PsiMethodCallExpression myOldCall;
  private final PsiMethodCallExpression myNewCall;
  private final int myDuplicatesNumber;

  public SignatureSuggesterPreviewDialog(PsiMethod oldMethod,
                                         PsiMethod newMethod,
                                         PsiMethodCallExpression oldMethodCall,
                                         PsiMethodCallExpression newMethodCall,
                                         int duplicatesNumber) {
    super(oldMethod.getProject());
    myOldMethod = oldMethod;
    myNewMethod = newMethod;
    myOldCall = oldMethodCall;
    myNewCall = newMethodCall;
    myDuplicatesNumber = duplicatesNumber;
    setTitle("Extract Parameters to Replace Duplicates");
    setOKButtonText("Accept Signature Change");
    setCancelButtonText("Keep Original Signature");
    init();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return new JLabel("<html><b>No exact method duplicates were found</b>, though changed method as shown below has " +
                      myDuplicatesNumber + " duplicate" + (myDuplicatesNumber > 1 ? "s" : "") + " </html>");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final Project project = myOldMethod.getProject();
    final VirtualFile file = PsiUtilCore.getVirtualFile(myOldMethod);

    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent oldContent = contentFactory.create(myOldMethod.getText() + "\n\n\nmethod call:\n " + myOldCall.getText(), file);
    DocumentContent newContent = contentFactory.create(myNewMethod.getText() + "\n\n\nmethod call:\n " + myNewCall.getText(), file);
    SimpleDiffRequest request = new SimpleDiffRequest(null, oldContent, newContent, "Before", "After");

    DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
    diffPanel.putContextHints(DiffUserDataKeys.PLACE, "ExtractSignature");
    diffPanel.setRequest(request);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(diffPanel.getComponent(), BorderLayout.CENTER);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(5)));
    return panel;
  }
}
