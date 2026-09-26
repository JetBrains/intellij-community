// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public class JavaClassReferenceSetTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNoDuplicates() {
    List<String> rootNames = new ArrayList<>(JavaClassReferenceProvider.getDefaultPackagesNames(getProject()));
    myFixture.configureByText(JavaFileType.INSTANCE, "class Test {\n" +
                                                     "    String foo = \"<caret>"+rootNames.get(0)+"."+rootNames.get(1)+"."+rootNames.get(2)+".XXXX13\";\n" +
                                                     "}");
    PsiLiteralExpression element = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLiteralExpression.class);
    String text = ElementManipulators.getValueText(element);

    PsiReference[] fromProvider = new JavaClassListReferenceProvider().getReferencesByString(text, element, 1);
    assertSize(4, fromProvider);

    JavaClassReferenceSet referenceSet = new JavaClassReferenceSet(text, element, 1, false, new JavaClassReferenceProvider());
    PsiReference[] references = referenceSet.getReferences();
    assertSize(4, references);
  }
}
