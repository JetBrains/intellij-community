/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The base class for all programming language support implementations. Specific language implementations should inherit from this class
 * and its register instance wrapped with {@link com.intellij.openapi.fileTypes.LanguageFileType} instance through
 * <code>FileTypeManager.getInstance().registerFileType</code>
 * There should be exactly one instance of each Language. It is usually created when creating {@link com.intellij.openapi.fileTypes.LanguageFileType} and can be retrieved later
 * with {@link #findInstance(Class)}.
 * For the list of standard languages, see {@link com.intellij.lang.StdLanguages}.
 */
public abstract class Language {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.Language");

  private static final Map<Class<? extends Language>, Language> ourRegisteredLanguages = new HashMap<Class<? extends Language>, Language>();
  private final Language myBaseLanguage;
  private final String myID;
  private final String[] myMimeTypes;
  public static final Language ANY = new Language("", "") { };

  private FileType myFileType;

  protected Language(@NonNls String id) {
    this(id, "");
  }

  protected Language(@NonNls final String ID, @NonNls final String... mimeTypes) {
    this(null, ID, mimeTypes);
  }

  protected Language(Language baseLanguage, @NonNls final String ID, @NonNls final String... mimeTypes) {
    myBaseLanguage = baseLanguage;
    myID = ID;
    myMimeTypes = mimeTypes;
    Class<? extends Language> langClass = getClass();

    if (ourRegisteredLanguages.containsKey(langClass)) {
      LOG.error("Language '" + langClass.getName() + "' is already registered");
      return;
    }
    ourRegisteredLanguages.put(langClass, this);
  }

  /**
   * @return collection of all languages registered so far.
   */
  public static Collection<Language> getRegisteredLanguages() {
    return Collections.unmodifiableCollection(ourRegisteredLanguages.values());
  }

  /**
   * @param klass <code>java.lang.Class</code> of the particular language. Serves key purpose.
   * @return instance of the <code>klass</code> language registered if any.
   */
  public static <T extends Language> T findInstance(Class<T> klass) {
    //noinspection unchecked
    return (T)ourRegisteredLanguages.get(klass);
  }


  public String toString() {
    //noinspection HardCodedStringLiteral
    return "Language: " + myID;
  }

  /**
   * Returns the list of MIME types corresponding to the language. The language MIME type is used for specifying the base language
   * of a JSP page.
   *
   * @return The list of MIME types.
   */
  public String[] getMimeTypes() {
    return myMimeTypes;
  }

  /**
   * Returns a user-readable name of the language.
   *
   * @return the name of the language.
   */
  @NotNull
  public String getID() {
    return myID;
  }

  @Nullable
  public FileType getAssociatedFileType() {
    return myFileType;
  }

  public void associateFileType(FileType type) {
    myFileType = type;
  }

  @Nullable
  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  public String getDisplayName() {
    return getID();
  }

  public final boolean is(Language another) {
    return this == another;
  }

  public boolean isCaseSensitive() {
    return false;
  }

  public final boolean isKindOf(Language another) {
    Language l = this;
    while (l != null) {
      if (l.is(another)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }
}
