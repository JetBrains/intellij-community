/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * The base class for all programming language support implementations. Specific language implementations should inherit from this class
 * and its register instance wrapped with {@link LanguageFileType} instance via {@code FileTypeManager.getInstance().registerFileType()}.
 * There should be exactly one instance of each Language.
 * It is usually created when creating {@link LanguageFileType} and can be retrieved later with {@link #findInstance(Class)}.
 * For the list of standard languages, see {@code com.intellij.lang.StdLanguages}.<p/>
 *
 * The language coming from file type can be changed by {@link com.intellij.psi.LanguageSubstitutor}
 */
public abstract class Language extends UserDataHolderBase {
  private static final Map<Class<? extends Language>, Language> ourRegisteredLanguages = ContainerUtil.newConcurrentMap();
  private static final ConcurrentMap<String, List<Language>> ourRegisteredMimeTypes = ContainerUtil.newConcurrentMap();
  private static final Map<String, Language> ourRegisteredIDs = ContainerUtil.newConcurrentMap();

  private final Language myBaseLanguage;
  private final String myID;
  private final String[] myMimeTypes;
  private final List<Language> myDialects = ContainerUtil.createLockFreeCopyOnWriteList();

  public static final Language ANY = new Language("") {
    @Override
    public String toString() {
      return "Language: ANY";
    }

    @Nullable
    @Override
    public LanguageFileType getAssociatedFileType() {
      return null;
    }
  };

  protected Language(@NotNull String ID) {
    this(ID, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  protected Language(@NotNull String ID, @NotNull String... mimeTypes) {
    this(null, ID, mimeTypes);
  }

  protected Language(@Nullable Language baseLanguage, @NotNull String ID, @NotNull String... mimeTypes) {
    myBaseLanguage = baseLanguage;
    myID = ID;
    myMimeTypes = mimeTypes.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : mimeTypes;

    Class<? extends Language> langClass = getClass();
    Language prev = ourRegisteredLanguages.put(langClass, this);
    if (prev != null) {
      throw new ImplementationConflictException("Language of '" + langClass + "' is already registered: " + prev, null, prev, this);
    }

    prev = ourRegisteredIDs.put(ID, this);
    if (prev != null) {
      throw new ImplementationConflictException("Language with ID '" + ID + "' is already registered: " + prev.getClass(), null, prev, this);
    }

    for (String mimeType : mimeTypes) {
      if (StringUtil.isEmpty(mimeType)) {
        continue;
      }
      List<Language> languagesByMimeType = ourRegisteredMimeTypes.get(mimeType);
      if (languagesByMimeType == null) {
        languagesByMimeType = ConcurrencyUtil.cacheOrGet(ourRegisteredMimeTypes, mimeType, ContainerUtil.createConcurrentList());
      }
      languagesByMimeType.add(this);
    }

    if (baseLanguage != null) {
      baseLanguage.myDialects.add(this);
    }
  }

  /**
   * @return collection of all languages registered so far.
   */
  @NotNull
  public static Collection<Language> getRegisteredLanguages() {
    final Collection<Language> languages = ourRegisteredLanguages.values();
    return Collections.unmodifiableCollection(new ArrayList<>(languages));
  }

  /**
   * @param klass {@code java.lang.Class} of the particular language. Serves key purpose.
   * @return instance of the {@code klass} language registered if any.
   */
  public static <T extends Language> T findInstance(@NotNull Class<T> klass) {
    @SuppressWarnings("unchecked") T t = (T)ourRegisteredLanguages.get(klass);
    return t;
  }

  /**
   * @param mimeType of the particular language.
   * @return collection of all languages for the given {@code mimeType}.
   */
  @NotNull
  public static Collection<Language> findInstancesByMimeType(@Nullable String mimeType) {
    List<Language> result = mimeType == null ? null : ourRegisteredMimeTypes.get(mimeType);
    return result == null ? Collections.emptyList() : Collections.unmodifiableCollection(result);
  }

  @Override
  public String toString() {
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
    final FileType[] types = FileTypeRegistry.getInstance().getRegisteredFileTypes();
    for (final FileType fileType : types) {
      if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() == this) {
        return (LanguageFileType)fileType;
      }
    }
    for (final FileType fileType : types) {
      if (fileType instanceof LanguageFileType && isKindOf(((LanguageFileType)fileType).getLanguage())) {
        return (LanguageFileType)fileType;
      }
    }
    return null;
  }

  @Nullable
  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  @NotNull
  public String getDisplayName() {
    return getID();
  }

  public final boolean is(Language another) {
    return this == another;
  }

  /**
   * @return whether identifiers in this language are case-sensitive. By default, delegates to the base language (if present) or returns false (otherwise).
   */
  public boolean isCaseSensitive() {
    return myBaseLanguage != null && myBaseLanguage.isCaseSensitive();
  }

  public final boolean isKindOf(Language another) {
    Language l = this;
    while (l != null) {
      if (l.is(another)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }

  public final boolean isKindOf(@NotNull String anotherLanguageId) {
    Language l = this;
    while (l != null) {
      if (l.getID().equals(anotherLanguageId)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }

  @NotNull
  public List<Language> getDialects() {
    return myDialects;
  }

  @Nullable
  public static Language findLanguageByID(String id) {
    return id == null ? null : ourRegisteredIDs.get(id);
  }

  /** Fake language identifier without registering */
  protected Language(@NotNull String ID, @SuppressWarnings("UnusedParameters") boolean register) {
    Language language = findLanguageByID(ID);
    if (language != null) {
      throw new IllegalArgumentException("Language with ID="+ID+" already registered: "+language+"; "+language.getClass());
    }
    myID = ID;
    myBaseLanguage = null;
    myMimeTypes = null;
  }
}