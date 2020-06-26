// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionListener;

/**
 * @author yole
 */
public final class JavaReferenceEditorUtil {
  private JavaReferenceEditorUtil() {
  }

  public static ReferenceEditorWithBrowseButton createReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                                                                      final String text,
                                                                                      final Project project,
                                                                                      final boolean toAcceptClasses) {
    return new ReferenceEditorWithBrowseButton(browseActionListener, project,
                                               (NullableFunction<String, Document>)s -> createDocument(s, project, toAcceptClasses), text);
  }

  @Nullable
  public static Document createDocument(final String text,
                                        Project project,
                                        boolean isClassesAccepted) {
    return createDocument(text, project, isClassesAccepted, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
  }

  @Nullable
  public static Document createDocument(final String text,
                                        Project project,
                                        boolean isClassesAccepted,
                                        JavaCodeFragment.VisibilityChecker visibilityChecker) {
    final PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(visibilityChecker);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Nullable
  public static Document createTypeDocument(final String text, Project project) {
    final PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createTypeCodeFragment(text, defaultPackage, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
}
