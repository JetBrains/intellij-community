/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;

  public LanguageExtension(@NonNls final String epName) {
    this(epName, null);
  }

  public LanguageExtension(@NonNls final String epName, T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
  }

  protected String keyToString(final Language key) {
    return key.getID();
  }

  public T forLanguage(Language l) {
    final List<T> extensions = forKey(l);
    if (extensions.isEmpty()) {
      return myDefaultImplementation;
    }
    else {
      return extensions.get(0);
    }
  }

  public List<T> allForLanguage(Language l) {
    return forKey(l);
  }
}