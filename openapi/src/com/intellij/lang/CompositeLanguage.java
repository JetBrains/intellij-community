package com.intellij.lang;

import com.intellij.psi.PsiFile;

import java.util.List;
import java.util.ArrayList;

public class CompositeLanguage extends Language{
  private List<LanguageExtension> myExtensions = new ArrayList<LanguageExtension>();

  protected CompositeLanguage(final String id) {
    super(id);
  }

  protected CompositeLanguage(final String ID, final String... mimeTypes) {
    super(ID, mimeTypes);
  }

  public void registerLanguageExtension(LanguageExtension extension){
    if(!myExtensions.contains(extension)) myExtensions.add(extension);
  }

  public Language[] getLanguageExtensionsForFile(final PsiFile psi) {
    final List<Language> extensions = new ArrayList<Language>(1);
    for (LanguageExtension extension : myExtensions) {
      if(extension.isRelevantForFile(psi)) extensions.add(extension.getLanguage());
    }
    return extensions.toArray(new Language[extensions.size()]);
  }

  public LanguageExtension[] getLanguageExtensions() {
    return myExtensions.toArray(new LanguageExtension[myExtensions.size()]);
  }
}
