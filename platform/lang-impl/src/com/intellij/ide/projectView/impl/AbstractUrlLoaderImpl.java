// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

final class AbstractUrlLoaderImpl implements AbstractUrl.AbstractUrlLoader {
  @SuppressWarnings("ResultOfObjectAllocationIgnored")
  @Override
  public void loadUrls() {
    new LibraryModuleGroupUrl(null);
    new ModuleGroupUrl(null);
    new PsiFileUrl(null);
    new DirectoryUrl(null, null);
    new NamedLibraryUrl(null, null);
    new ModuleUrl(null, null);
  }
}
