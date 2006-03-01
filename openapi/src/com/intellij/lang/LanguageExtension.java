package com.intellij.lang;

import com.intellij.psi.PsiFile;
import com.intellij.pom.xml.events.XmlChange;

public interface LanguageExtension {
  boolean isRelevantForFile(final PsiFile psi);
  Language getLanguage();
  boolean isAffectedByChange(final XmlChange xmlChange);
}
