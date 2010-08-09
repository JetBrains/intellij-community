/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * The base class for all programming language support implementations. Specific language implementations should inherit from this class
 * and its register instance wrapped with {@link com.intellij.openapi.fileTypes.LanguageFileType} instance through
 * <code>FileTypeManager.getInstance().registerFileType</code>
 * There should be exactly one instance of each Language. It is usually created when creating {@link com.intellij.openapi.fileTypes.LanguageFileType} and can be retrieved later
 * with {@link #findInstance(Class)}.
 * For the list of standard languages, see {@link com.intellij.lang.StdLanguages}.
 */
public abstract class Language extends UserDataHolderBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.Language");

  private static final Map<Class<? extends Language>, Language> ourRegisteredLanguages = new THashMap<Class<? extends Language>, Language>();
  private static final Map<String, Language> ourRegisteredIDs = new THashMap<String, Language>();
  private final Language myBaseLanguage;
  private final String myID;
  private final String[] myMimeTypes;
  public static final Language ANY = new Language("") { };

  protected Language(@NotNull @NonNls String id) {
    this(id, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  protected Language(@NotNull @NonNls final String ID, @NotNull @NonNls final String... mimeTypes) {
    this(null, ID, mimeTypes);
  }

  protected Language(@Nullable Language baseLanguage, @NotNull @NonNls final String ID, @NotNull @NonNls final String... mimeTypes) {
    myBaseLanguage = baseLanguage;
    myID = ID;
    myMimeTypes = mimeTypes;
    Class<? extends Language> langClass = getClass();
    Language prev = ourRegisteredLanguages.put(langClass, this);
    if (prev != null) {
      LOG.error("Language of '" + langClass + "' is already registered: "+prev);
      return;
    }
    prev = ourRegisteredIDs.put(ID, this);
    if (prev != null) {
      LOG.error("Language with ID '" + ID + "' is already registered: "+prev.getClass());
    }
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
  @NotNull
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
  public LanguageFileType getAssociatedFileType() {
    final FileType[] types = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (final FileType fileType : types) {
      if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() == this) {
        return (LanguageFileType)fileType;
      }
    }
    for (final FileType fileType : types) {
      if (fileType instanceof LanguageFileType && isKindOf(((LanguageFileType)fileType).getLanguage())) {
        return (LanguageFileType) fileType;
      }
    }
    return null;
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
    return myBaseLanguage != null ? myBaseLanguage.isCaseSensitive() : false;
  }

  public final boolean isKindOf(Language another) {
    Language l = this;
    while (l != null) {
      if (l.is(another)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }

  public static @Nullable Language findLanguageByID(String id) {
    final Collection<Language> languages = Language.getRegisteredLanguages();
    for (Language language : languages) {
      if (language.getID().equals(id)) {
        return language;
      }
    }
    return null;
  }
}
