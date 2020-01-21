// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.jsp;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;

public interface JspxFileViewProvider extends TemplateLanguageFileViewProvider {
  Language JAVA_HOLDER_METHOD_TREE_LANGUAGE = new JavaHolderMethodTreeLanguage();

  class JavaHolderMethodTreeLanguage extends Language implements DependentLanguage{
    public JavaHolderMethodTreeLanguage() {
      super("JAVA_HOLDER_METHOD_TREE", "");
    }
  }
}
