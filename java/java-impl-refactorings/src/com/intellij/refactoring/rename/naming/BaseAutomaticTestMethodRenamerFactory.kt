// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming

import com.intellij.java.refactoring.JavaRefactoringBundle

abstract class BaseAutomaticTestMethodRenamerFactory : AutomaticRenamerFactory {
  override fun getOptionName(): String = JavaRefactoringBundle.message("rename.test.method")
}