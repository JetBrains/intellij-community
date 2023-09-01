// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A language represents a programming language such as Java,
 * a document format such as HTML or Markdown,
 * or other structured data formats such as HTTP cookies or regular expressions.
 * <p>
 * An implementation for a specific language should inherit from this class
 * and wrap its registered instance with {@link LanguageFileType}
 * via the {@code com.intellij.fileType} extension point.
 * <p>
 * There should be exactly one instance of each Language.
 * It is usually created when creating {@link LanguageFileType} and can be retrieved later with {@link #findInstance(Class)}.
 * <p>
 * The language coming from file type can be changed by {@link com.intellij.psi.LanguageSubstitutor}.
 */
public abstract class Language extends UserDataHolderBase {
  public static final Language[] EMPTY_ARRAY = new Language[0];
  private static final Map<Class<? extends Language>, Language> ourRegisteredLanguages = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, List<Language>> ourRegisteredMimeTypes = new ConcurrentHashMap<>();
  private static final Map<String, Language> ourRegisteredIDs = new ConcurrentHashMap<>();

  private final Language myBaseLanguage;
  private final String myID;
  private final String[] myMimeTypes;
  private final List<Language> myDialects = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Set<@NotNull Language> myTransitiveDialects = ContainerUtil.newConcurrentSet();

  public static final Language ANY = new Language("") {
    @Override
    public String toString() {
      return "Language: ANY";
    }

    @Override
    public @Nullable LanguageFileType getAssociatedFileType() {
      return null;
    }
  };

  protected Language(@NonNls @NotNull String ID) {
    this(ID, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  protected Language(@NonNls @NotNull String ID, @NonNls @NotNull String @NotNull ... mimeTypes) {
    this(null, ID, mimeTypes);
  }

  protected Language(@Nullable Language baseLanguage, @NonNls @NotNull String ID, @NonNls @NotNull String @NotNull ... mimeTypes) {
    if (baseLanguage instanceof MetaLanguage) {
      throw new ImplementationConflictException(
        "MetaLanguage cannot be a base language.\n" +
        "This language: '" + ID + "'\n" +
        "Base language: '" + baseLanguage.getID() + "'",
        null, this, baseLanguage
      );
    }
    myBaseLanguage = baseLanguage;
    myID = ID;
    myMimeTypes = mimeTypes.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : mimeTypes;

    Class<? extends Language> langClass = getClass();
    Language prev = ourRegisteredLanguages.putIfAbsent(langClass, this);
    if (prev != null) {
      throw new ImplementationConflictException("Language of '" + langClass + "' is already registered: " + prev, null, prev, this);
    }

    prev = ourRegisteredIDs.putIfAbsent(ID, this);
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
      while (baseLanguage != null) {
        baseLanguage.myTransitiveDialects.add(this);
        baseLanguage = baseLanguage.getBaseLanguage();
      }
    }
  }

  /**
   * @return collection of all languages registered so far.
   */
  public static @NotNull Collection<Language> getRegisteredLanguages() {
    Collection<Language> languages = ourRegisteredLanguages.values();
    return Collections.unmodifiableCollection(new ArrayList<>(languages));
  }

  @ApiStatus.Internal
  public static void unregisterAllLanguagesIn(@NotNull ClassLoader classLoader, @NotNull PluginDescriptor pluginDescriptor) {
    for (Map.Entry<Class<? extends Language>, Language> e : new ArrayList<>(ourRegisteredLanguages.entrySet())) {
      Class<? extends Language> clazz = e.getKey();
      Language language = e.getValue();
      if (clazz.getClassLoader() == classLoader) {
        language.unregisterLanguage(pluginDescriptor);
      }
    }
    IElementType.unregisterElementTypes(classLoader, pluginDescriptor);
  }

  @ApiStatus.Internal
  public void unregisterLanguage(@NotNull PluginDescriptor pluginDescriptor) {
    IElementType.unregisterElementTypes(this, pluginDescriptor);
    Application application = ApplicationManager.getApplication();
    ReferenceProvidersRegistry referenceProvidersRegistry = application == null ? null : application.getServiceIfCreated(ReferenceProvidersRegistry.class);
    if (referenceProvidersRegistry != null) {
      referenceProvidersRegistry.unloadProvidersFor(this);
    }
    ourRegisteredLanguages.remove(getClass());
    ourRegisteredIDs.remove(getID());
    for (String mimeType : getMimeTypes()) {
      ourRegisteredMimeTypes.remove(mimeType);
    }
    Language baseLanguage = getBaseLanguage();
    if (baseLanguage != null) {
      baseLanguage.unregisterDialect(this);
    }
  }

  @ApiStatus.Internal
  public void unregisterDialect(@NotNull Language language) {
    myDialects.remove(language);
    for (Language baseLanguage = this; baseLanguage != null; baseLanguage = baseLanguage.getBaseLanguage()) {
      baseLanguage.myTransitiveDialects.remove(language);
    }
  }

  /**
   * @param klass {@code java.lang.Class} of the particular language. Serves key purpose.
   * @return instance of the {@code klass} language registered if any.
   */
  public static <T extends Language> T findInstance(@NotNull Class<T> klass) {
    //noinspection unchecked
    return (T)ourRegisteredLanguages.get(klass);
  }

  /**
   * @param mimeType of the particular language.
   * @return unmodifiable collection of all languages for the given {@code mimeType}.
   */
  @Unmodifiable
  public static @NotNull Collection<Language> findInstancesByMimeType(@Nullable String mimeType) {
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
  public String @NotNull [] getMimeTypes() {
    return myMimeTypes;
  }

  /**
   * Returns a user-readable name of the language (language names are not localized).
   *
   * @return the name of the language.
   */
  public @NotNull @NlsSafe String getID() {
    return myID;
  }

  public @Nullable LanguageFileType getAssociatedFileType() {
    return FileTypeRegistry.getInstance().findFileTypeByLanguage(this);
  }

  @ApiStatus.Internal
  public @Nullable LanguageFileType findMyFileType(FileType @NotNull [] types) {
    for (FileType fileType : types) {
      if (fileType instanceof LanguageFileType) {
        LanguageFileType languageFileType = (LanguageFileType)fileType;
        if (languageFileType.getLanguage() == this && !languageFileType.isSecondary()) {
          return languageFileType;
        }
      }
    }
    for (FileType fileType : types) {
      if (fileType instanceof LanguageFileType) {
        LanguageFileType languageFileType = (LanguageFileType)fileType;
        if (isKindOf(languageFileType.getLanguage()) && !languageFileType.isSecondary()) {
          return languageFileType;
        }
      }
    }
    return null;
  }


  public @Nullable Language getBaseLanguage() {
    return myBaseLanguage;
  }

  public @NotNull @NlsSafe String getDisplayName() {
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

  @Contract(pure = true)
  public final boolean isKindOf(Language another) {
    Language l = this;
    while (l != null) {
      if (l.is(another)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }

  public final boolean isKindOf(@NotNull @NonNls String anotherLanguageId) {
    Language l = this;
    while (l != null) {
      if (l.getID().equals(anotherLanguageId)) return true;
      l = l.getBaseLanguage();
    }
    return false;
  }

  @Unmodifiable
  public @NotNull List<Language> getDialects() {
    return Collections.unmodifiableList(myDialects);
  }

  public static @Nullable Language findLanguageByID(@NonNls String id) {
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

  /**
   * Mark {@code dialect} as a language dialect of this language.
   * Please don't forget to call {@link #unregisterDialect(Language)} later,
   * because this dialect won't de-register itself automatically on dispose because it doesn't know about this language being its parent,
   * which can cause memory leaks.
   */
  @ApiStatus.Internal
  protected void registerDialect(@NotNull Language dialect) {
    myDialects.add(dialect);
    for (Language baseLanguage = this; baseLanguage != null; baseLanguage = baseLanguage.getBaseLanguage()) {
      baseLanguage.myTransitiveDialects.add(dialect);
    }
  }

  /**
   * @return this language dialects and their dialects transitively
   */
  @Unmodifiable
  @NotNull
  public Collection<@NotNull Language> getTransitiveDialects() {
    return Collections.unmodifiableSet(myTransitiveDialects);
  }
}