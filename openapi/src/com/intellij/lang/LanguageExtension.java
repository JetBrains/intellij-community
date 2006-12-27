package com.intellij.lang;

import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.PsiFile;

public interface LanguageExtension {
  boolean isRelevantForFile(final PsiFile psi);
  Language getLanguage();
  void updateByChange(final XmlChange xmlChange);
}
