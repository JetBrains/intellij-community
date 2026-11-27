// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.util;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.mock.MockPsiDirectory;
import com.intellij.mock.MockPsiElement;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RefactoringUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  private Disposable testRootDisposable;

  @BeforeEach
  void setUp(@TestDisposable Disposable disposable) {
    testRootDisposable = disposable;
  }

  @Test
  public void testEmptyNameIsInvalid() {
    PsiElement e = new MockPsiElement(testRootDisposable);
    assertFalse(isValid(e, null));
    assertFalse(isValid(e, ""));
  }

  @Test
  public void testNameValidationForFileAndDirectory() {
    doTestNameValidationForFileOrDir(new MockPsiFile(PsiManager.getInstance(projectFixture.get().getProject())));
    doTestNameValidationForFileOrDir(new MockPsiDirectory(projectFixture.get().getProject(), testRootDisposable));
  }

  private static void doTestNameValidationForFileOrDir(PsiElement element) {
    assertTrue(isValid(element, "name"));
    assertFalse(isValid(element, "one/two"));
    assertFalse(isValid(element, "one\\two"));
  }

  @Test
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

  @Test
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

    LanguageNamesValidation.INSTANCE.addExplicitExtension(l, v, testRootDisposable);

    return l;
  }

  private static boolean isValid(PsiElement e, String name) {
    return RenameUtil.isValidName(projectFixture.get().getProject(), e, name);
  }
}