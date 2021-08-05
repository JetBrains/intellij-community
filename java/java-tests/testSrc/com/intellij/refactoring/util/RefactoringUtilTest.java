// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.mock.MockPsiDirectory;
import com.intellij.mock.MockPsiElement;
import com.intellij.mock.MockPsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.LightIdeaTestCase;

import static org.easymock.EasyMock.*;

public class RefactoringUtilTest extends LightIdeaTestCase {
  public void testEmptyNameIsInvalid() {
    PsiElement e = new MockPsiElement(getTestRootDisposable());
    assertFalse(isValid(e, null));
    assertFalse(isValid(e, ""));
  }

  public void testNameValidationForFileAndDirectory() {
    doTestNameValidationForFileOrDir(new MockPsiFile(getPsiManager()));
    doTestNameValidationForFileOrDir(new MockPsiDirectory(getProject(), getTestRootDisposable()));
  }

  private void doTestNameValidationForFileOrDir(PsiElement element) {
    assertTrue(isValid(element, "name"));
    assertFalse(isValid(element, "one/two"));
    assertFalse(isValid(element, "one\\two"));
  }

  public void testValidationOfLanguageElementWithContainingFile() {
    Language l = createLanguageWithNamesValidator("good", "bad");

    PsiFile f = createMock(PsiFile.class);
    expect(f.getLanguage()).andReturn(l).anyTimes();
    replay(f);

    PsiElement e = createMock(PsiField.class);
    expect(e.getContainingFile()).andStubReturn(f);
    expect(e.getLanguage()).andReturn(l).anyTimes();
    replay(e);

    assertTrue(isValid(e, "good"));
    assertFalse(isValid(e, "bad"));
  }

  public void testValidationOfLanguageElementWithoutContainingFile() {
    Language l = createLanguageWithNamesValidator("good", "bad");

    PsiElement e = createMock(PsiField.class);
    expect(e.getContainingFile()).andStubReturn(null);
    expect(e.getLanguage()).andReturn(l).anyTimes();
    replay(e);

    assertTrue(isValid(e, "good"));
    assertFalse(isValid(e, "bad"));
  }

  private Language createLanguageWithNamesValidator(String goodName, String badName) {
    NamesValidator v = createMock(NamesValidator.class);
    //noinspection ConstantConditions
    expect(v.isIdentifier(eq(goodName), anyObject())).andStubReturn(true);
    //noinspection ConstantConditions
    expect(v.isIdentifier(eq(badName), anyObject())).andStubReturn(false);
    replay(v);

    Language l = createMock(Language.class);
    expect(l.getID()).andStubReturn("MOCK_LANGUAGE_DIALECT");
    expect(l.getBaseLanguage()).andStubReturn(null);
    expect(l.getUserData(anyObject())).andReturn(null).anyTimes();
    l.putUserData(anyObject(), anyObject());
    expectLastCall().anyTimes();
    expect(l.putUserDataIfAbsent(anyObject(), anyObject())).andAnswer(() -> getCurrentArguments()[1]).anyTimes(); // return second parameter
    replay(l);

    LanguageNamesValidation.INSTANCE.addExplicitExtension(l, v, getTestRootDisposable());

    return l;
  }

  private boolean isValid(PsiElement e, String name) {
    return RenameUtil.isValidName(getProject(), e, name);
  }
}