// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

import com.intellij.refactoring.classMembers.ClassMembersRefactoringSupport;

/**
 * @author Dennis.Ushakov
 */
public final class LanguageDependentMembersRefactoringSupport extends LanguageExtension<ClassMembersRefactoringSupport> {
  public static final LanguageDependentMembersRefactoringSupport INSTANCE = new LanguageDependentMembersRefactoringSupport();

  private LanguageDependentMembersRefactoringSupport() {
    super("com.intellij.lang.refactoringSupport.classMembersRefactoringSupport");
  }
}
