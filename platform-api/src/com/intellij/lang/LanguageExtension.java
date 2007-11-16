/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
    List<T> extensions = forKey(l);
    if (extensions.isEmpty()) {

      Language base = l.getBaseLanguage();
      if (base != null) {
        return forLanguage(base);
      }

      return myDefaultImplementation;
    }
    else {
      return extensions.get(0);
    }
  }

  @NotNull
  public List<T> allForLanguage(Language l) {
    List<T> list = forKey(l);
    if (list.isEmpty()) {
      Language base = l.getBaseLanguage();
      if (base != null) {
        return allForLanguage(base);
      }
    }
    return list;
  }
}