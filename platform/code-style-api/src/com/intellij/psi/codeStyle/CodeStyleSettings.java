// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.CodeStyleBundle;
import com.intellij.configurationStore.Property;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.PresentableEnum;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A container for global, language and custom code style settings and indent options. Global options are default options for multiple
 * languages and language-independent settings. Global (default) options which may be overwritten by a specific language can be retrieved
 * using {@code getDefault...()} methods. Use {@link #getCommonSettings(Language)} to retrieve code style options for a language. Some
 * languages may have specific options which are stored in a class derived from {@link CustomCodeStyleSettings}.
 * Use {@link #getCustomSettings(Class)} to access them. For indent options use one of {@code getIndentOptions(...)} methods. In most cases
 * you need {@link #getIndentOptionsByFile(PsiFile)}.
 * </p>
 * <p>
 * Consider also using an utility {@link com.intellij.application.options.CodeStyle} class which encapsulates the above methods where possible.
 * </p>
 *
 * <b>Note:</b> A direct use of any non-final public fields from {@code CodeStyleSettings} class is strongly discouraged. These fields,
 * as well as the inheritance from {@code CommonCodeStyleSettings}, are left only for backwards compatibility and may be removed in the future.
 */
public class CodeStyleSettings extends LegacyCodeStyleSettings implements Cloneable, JDOMExternalizable, ImportsLayoutSettings {
  public static final int CURR_VERSION = 173;

  private static final Logger LOG = Logger.getInstance(CodeStyleSettings.class);
  public static final String VERSION_ATTR = "version";

  @NonNls private static final String REPEAT_ANNOTATIONS = "REPEAT_ANNOTATIONS";
  @NonNls static final String ADDITIONAL_INDENT_OPTIONS = "ADDITIONAL_INDENT_OPTIONS";

  @NonNls private static final String FILETYPE = "fileType";
  private CommonCodeStyleSettingsManager myCommonSettingsManager = new CommonCodeStyleSettingsManager(this);
  private final CustomCodeStyleSettingsManager myCustomCodeStyleSettingsManager = new CustomCodeStyleSettingsManager(this);

  private static class DefaultsHolder {
    private static final CodeStyleSettings myDefaults = new CodeStyleSettings(true, false);
  }

  private final SoftMargins mySoftMargins = new SoftMargins();

  private final ExcludedFiles myExcludedFiles = new ExcludedFiles();

  private int myVersion = CURR_VERSION;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  /**
   * @deprecated Use {@link CodeStyleSettingsManager#createSettings()}or {@link CodeStyleSettingsManager#createTemporarySettings()}.
   * <p>
   * For test purposes use {@code CodeStyle.createTestSettings()}
   */
  @Deprecated
  public CodeStyleSettings() {
    this(true, true);
  }

  /**
   * @deprecated See {@link #CodeStyleSettings()}
   */
  @Deprecated(forRemoval = true)
  public CodeStyleSettings(boolean loadExtensions) {
    this(loadExtensions, true);
  }

  /**
   * @param loadExtensions    Loading custom extensions {@link CustomCodeStyleSettings} is needed.
   * @param needsRegistration Created settings need to be registered to avoid memory leaks when a plugin with custom
   *                          code style extensions is unloaded. Can be {@code false} for temporarily created settings.
   */
  protected CodeStyleSettings(boolean loadExtensions, boolean needsRegistration) {
    initImportsByDefault();

    if (loadExtensions) {
      myCustomCodeStyleSettingsManager.initCustomSettings();
    }

    if (needsRegistration) {
      CodeStyleSettingsManager.registerSettings(this);
    }
  }

  private void initImportsByDefault() {
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "java.awt", false));
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false,"javax.swing", false));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "javax", true));
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
  }

  public void setParentSettings(CodeStyleSettings parent) {
    myParentSettings = parent;
  }

  public CodeStyleSettings getParentSettings() {
    return myParentSettings;
  }

  @NotNull
  public <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> aClass) {
    return myCustomCodeStyleSettingsManager.getCustomSettings(aClass);
  }

  /**
   * This method can return null during plugin unloading, if the requested settings have already been unregistered.
   */
  @Nullable
  public <T extends CustomCodeStyleSettings> T getCustomSettingsIfCreated(@NotNull Class<T> aClass) {
    return myCustomCodeStyleSettingsManager.getCustomSettingsIfCreated(aClass);
  }

  /**
   * @deprecated
   * For short-lived temporary settings use {@code CodeStyle.doWithTemporarySettings(project,baseSettings,modifier,runnable},
   * for permanently created settings use {@link CodeStyleSettingsManager#cloneSettings(CodeStyleSettings)}
   */
  @Deprecated
  public CodeStyleSettings clone() {
    CodeStyleSettings clone = new CodeStyleSettings(true, true);
    clone.copyFrom(this);
    return clone;
  }

  private void copyCustomSettingsFrom(@NotNull CodeStyleSettings from) {
    myCustomCodeStyleSettingsManager.copyFrom(from);

    PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(from.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    IMPORT_LAYOUT_TABLE.copyFrom(from.IMPORT_LAYOUT_TABLE);

    OTHER_INDENT_OPTIONS.copyFrom(from.OTHER_INDENT_OPTIONS);

    myAdditionalIndentOptions.clear();
    for (Map.Entry<FileType, IndentOptions> optionEntry : from.myAdditionalIndentOptions.entrySet()) {
      IndentOptions options = optionEntry.getValue();
      myAdditionalIndentOptions.put(optionEntry.getKey(), (IndentOptions)options.clone());
    }

    myCommonSettingsManager = from.myCommonSettingsManager.clone(this);

    myRepeatAnnotations.clear();
    myRepeatAnnotations.addAll(from.myRepeatAnnotations);
  }

  public void copyFrom(CodeStyleSettings from) {
    CommonCodeStyleSettings.copyPublicFields(from, this);
    CommonCodeStyleSettings.copyPublicFields(from.OTHER_INDENT_OPTIONS, OTHER_INDENT_OPTIONS);
    mySoftMargins.setValues(from.getDefaultSoftMargins());
    myExcludedFiles.setDescriptors(from.getExcludedFiles().getDescriptors());
    copyCustomSettingsFrom(from);
  }

  public boolean AUTODETECT_INDENTS = true;

  public final IndentOptions OTHER_INDENT_OPTIONS = new IndentOptions();

  private final Map<FileType,IndentOptions> myAdditionalIndentOptions = new LinkedHashMap<>();

  private static final String ourSystemLineSeparator = System.lineSeparator();

  /**
   * Line separator. It can be null if choosen line separator is "System-dependent"!
   */
  public String LINE_SEPARATOR;

  /**
   * @return line separator. If the chosen line separator is "System-dependent", return default separator for this OS.
   */
  public String getLineSeparator() {
    return LINE_SEPARATOR != null ? LINE_SEPARATOR : ourSystemLineSeparator;
  }


// region Java settings (legacy)
//----------------- NAMING CONVENTIONS --------------------

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#FIELD_NAME_PREFIX} */
  @Deprecated
  public String FIELD_NAME_PREFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#STATIC_FIELD_NAME_PREFIX} */
  @Deprecated(forRemoval = true)
  public String STATIC_FIELD_NAME_PREFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#PARAMETER_NAME_PREFIX} */
  @Deprecated(forRemoval = true)
  public String PARAMETER_NAME_PREFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#LOCAL_VARIABLE_NAME_PREFIX} */
  @Deprecated(forRemoval = true)
  public String LOCAL_VARIABLE_NAME_PREFIX = "";

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#FIELD_NAME_SUFFIX} */
  @Deprecated(forRemoval = true)
  public String FIELD_NAME_SUFFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#STATIC_FIELD_NAME_SUFFIX} */
  @Deprecated(forRemoval = true)
  public String STATIC_FIELD_NAME_SUFFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#PARAMETER_NAME_SUFFIX} */
  @Deprecated(forRemoval = true)
  public String PARAMETER_NAME_SUFFIX = "";
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#LOCAL_VARIABLE_NAME_SUFFIX} */
  @Deprecated(forRemoval = true)
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#PREFER_LONGER_NAMES} */
  @Deprecated(forRemoval = true)
  public boolean PREFER_LONGER_NAMES = true;

//----------------- 'final' modifier settings -------
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#GENERATE_FINAL_LOCALS} */
  @Deprecated(forRemoval = true)
  public boolean GENERATE_FINAL_LOCALS;
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#GENERATE_FINAL_PARAMETERS} */
  @Deprecated(forRemoval = true)
  public boolean GENERATE_FINAL_PARAMETERS;

//----------------- visibility -----------------------------
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#VISIBILITY} */
  @Deprecated(forRemoval = true)
  public String VISIBILITY = "public";

//----------------- generate parentheses around method arguments ----------
  /** @deprecated Use RubyCodeStyleSettings.PARENTHESES_AROUND_METHOD_ARGUMENTS */
  @Deprecated(forRemoval = true)
  public boolean PARENTHESES_AROUND_METHOD_ARGUMENTS = true;

//----------------- annotations ----------------
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#USE_EXTERNAL_ANNOTATIONS} */
  @Deprecated(forRemoval = true)
  public boolean USE_EXTERNAL_ANNOTATIONS;
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#INSERT_OVERRIDE_ANNOTATION} */
  @Deprecated(forRemoval = true)
  public boolean INSERT_OVERRIDE_ANNOTATION = true;

//----------------- override -------------------
  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#REPEAT_SYNCHRONIZED} */
  @Deprecated(forRemoval = true)
  public boolean REPEAT_SYNCHRONIZED = true;

  private final List<String> myRepeatAnnotations = new ArrayList<>();

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#getRepeatAnnotations()} */
  @Deprecated(forRemoval = true)
  public List<String> getRepeatAnnotations() {
    return myRepeatAnnotations;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setRepeatAnnotations(List)} */
  @Deprecated(forRemoval = true)
  public void setRepeatAnnotations(List<String> repeatAnnotations) {
    myRepeatAnnotations.clear();
    myRepeatAnnotations.addAll(repeatAnnotations);
  }

  //----------------- JAVA IMPORTS (deprecated, moved to JavaCodeStyleSettings) --------------------

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#LAYOUT_STATIC_IMPORTS_SEPARATELY} */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#USE_FQ_CLASS_NAMES} */
  @Deprecated(forRemoval = true)
  public boolean USE_FQ_CLASS_NAMES;

  /** @deprecated use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#CLASS_NAMES_IN_JAVADOC} instead */
  @Deprecated(forRemoval = true)
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#USE_SINGLE_CLASS_IMPORTS */
  @Deprecated(forRemoval = true)
  public boolean USE_SINGLE_CLASS_IMPORTS = true;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#INSERT_INNER_CLASS_IMPORTS */
  @Deprecated(forRemoval = true)
  public boolean INSERT_INNER_CLASS_IMPORTS;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND */
  @Deprecated(forRemoval = true)
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND */
  @Deprecated(forRemoval = true)
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#PACKAGES_TO_USE_IMPORT_ON_DEMAND */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#IMPORT_LAYOUT_TABLE */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#isLayoutStaticImportsSeparately()} */
  @Override
  @Deprecated
  public boolean isLayoutStaticImportsSeparately() {
    return LAYOUT_STATIC_IMPORTS_SEPARATELY;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setLayoutStaticImportsSeparately(boolean)} */
  @Override
  @Deprecated
  public void setLayoutStaticImportsSeparately(boolean value) {
    LAYOUT_STATIC_IMPORTS_SEPARATELY = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#getNamesCountToUseImportOnDemand()} */
  @Deprecated
  @Override
  public int getNamesCountToUseImportOnDemand() {
    return NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setNamesCountToUseImportOnDemand(int)}  */
  @Deprecated
  @Override
  public void setNamesCountToUseImportOnDemand(int value) {
    NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#getClassCountToUseImportOnDemand()} */
  @Deprecated
  @Override
  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setClassCountToUseImportOnDemand(int)} */
  @Deprecated
  @Override
  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#isInsertInnerClassImports()} */
  @Deprecated
  @Override
  public boolean isInsertInnerClassImports() {
    return INSERT_INNER_CLASS_IMPORTS;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setInsertInnerClassImports(boolean)} */
  @Deprecated
  @Override
  public void setInsertInnerClassImports(boolean value) {
    INSERT_INNER_CLASS_IMPORTS = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#isUseSingleClassImports()} */
  @Deprecated
  @Override
  public boolean isUseSingleClassImports() {
    return USE_SINGLE_CLASS_IMPORTS;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setUseSingleClassImports(boolean)} */
  @Deprecated
  @Override
  public void setUseSingleClassImports(boolean value) {
    USE_SINGLE_CLASS_IMPORTS = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#isUseFqClassNames()} */
  @Deprecated
  @Override
  public boolean isUseFqClassNames() {
    return USE_FQ_CLASS_NAMES;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#setUseFqClassNames(boolean)} */
  @Deprecated
  @Override
  public void setUseFqClassNames(boolean value) {
    USE_FQ_CLASS_NAMES = value;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#getImportLayoutTable()} */
  @Deprecated
  @Override
  public PackageEntryTable getImportLayoutTable() {
    return IMPORT_LAYOUT_TABLE;
  }

  /** @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#getPackagesToUseImportOnDemand()} */
  @Deprecated
  @Override
  public PackageEntryTable getPackagesToUseImportOnDemand() {
    return PACKAGES_TO_USE_IMPORT_ON_DEMAND;
  }

  // endregion

// region ORDER OF MEMBERS

  @Deprecated(forRemoval = true) public int STATIC_FIELDS_ORDER_WEIGHT = 1;
  @Deprecated(forRemoval = true) public int FIELDS_ORDER_WEIGHT = 2;
  @Deprecated(forRemoval = true) public int CONSTRUCTORS_ORDER_WEIGHT = 3;
  @Deprecated(forRemoval = true) public int STATIC_METHODS_ORDER_WEIGHT = 4;
  @Deprecated(forRemoval = true) public int METHODS_ORDER_WEIGHT = 5;
  @Deprecated(forRemoval = true) public int STATIC_INNER_CLASSES_ORDER_WEIGHT = 6;
  @Deprecated(forRemoval = true) public int INNER_CLASSES_ORDER_WEIGHT = 7;

// endregion

// region WRAPPING

  /**
   * <b>Do not use this field directly since it doesn't reflect a setting for a specific language which may
   * overwrite this one. Call {@link #getRightMargin(Language)} method instead.</b>
   */
  @ApiStatus.Internal
  @Property(externalName = "max_line_length")
  public int RIGHT_MARGIN = 120;

  /**
   * <b>Do not use this field directly since it doesn't reflect a setting for a specific language which may
   * overwrite this one. Call {@link #isWrapOnTyping(Language)} method instead.</b>
   *
   * @see CommonCodeStyleSettings#WRAP_ON_TYPING
   */
  @Property(externalName = "wrap_on_typing")
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;

// endregion

// region Javadoc formatting options

  /**
   * @deprecated  Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#ENABLE_JAVADOC_FORMATTING}
   */
  @Deprecated(forRemoval = true)
  public boolean ENABLE_JAVADOC_FORMATTING = true;

  /**
   * @deprecated Use {@link com.intellij.psi.codeStyle.JavaCodeStyleSettings#JD_LEADING_ASTERISKS_ARE_ENABLED}
   */
  @Deprecated(forRemoval = true)
  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;


// endregion

  /** @deprecated Use {@link com.intellij.application.options.JspCodeStyleSettings#JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST} */
  @Deprecated(forRemoval = true)
  public boolean JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST;

  //----------------------------------------------------------------------------------------

  // region Formatter control

  public boolean FORMATTER_TAGS_ENABLED;
  public String FORMATTER_ON_TAG = "@formatter:on";
  public String FORMATTER_OFF_TAG = "@formatter:off";

  public volatile boolean FORMATTER_TAGS_ACCEPT_REGEXP;
  private volatile Pattern myFormatterOffPattern;
  private volatile Pattern myFormatterOnPattern;

  @Nullable
  public Pattern getFormatterOffPattern() {
    if (myFormatterOffPattern == null && FORMATTER_TAGS_ENABLED && FORMATTER_TAGS_ACCEPT_REGEXP) {
      myFormatterOffPattern = getPatternOrDisableRegexp(FORMATTER_OFF_TAG);
    }
    return myFormatterOffPattern;
  }

  public void setFormatterOffPattern(@Nullable Pattern formatterOffPattern) {
    myFormatterOffPattern = formatterOffPattern;
  }

  @Nullable
  public Pattern getFormatterOnPattern() {
    if (myFormatterOffPattern == null && FORMATTER_TAGS_ENABLED && FORMATTER_TAGS_ACCEPT_REGEXP) {
      myFormatterOnPattern = getPatternOrDisableRegexp(FORMATTER_ON_TAG);
    }
    return myFormatterOnPattern;
  }

  public void setFormatterOnPattern(@Nullable Pattern formatterOnPattern) {
    myFormatterOnPattern = formatterOnPattern;
  }

  @Nullable
  private Pattern getPatternOrDisableRegexp(@NotNull String markerText) {
    try {
      return Pattern.compile(markerText);
    }
    catch (PatternSyntaxException pse) {
      LOG.error("Loaded regexp pattern is invalid: '" + markerText + "', error message: " + pse.getMessage());
      FORMATTER_TAGS_ACCEPT_REGEXP = false;
      return null;
    }
  }

  public boolean ENABLE_SECOND_REFORMAT;
  // endregion

  //----------------------------------------------------------------------------------------

  private CodeStyleSettings myParentSettings;
  private boolean myLoadedAdditionalIndentOptions;

  private static void setVersion(@NotNull Element element, int version) {
    element.setAttribute(VERSION_ATTR, Integer.toString(version));
  }

  private static int getVersion(@NotNull Element element) {
    String versionStr = element.getAttributeValue(VERSION_ATTR);
    if (versionStr == null) {
      return 0;
    }
    else {
      try {
        return Integer.parseInt(versionStr);
      }
      catch (NumberFormatException nfe) {
        return CURR_VERSION;
      }
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myVersion = getVersion(element);
    myCustomCodeStyleSettingsManager.notifySettingsBeforeLoading();
    DefaultJDOMExternalizer.readExternal(this, element);
    if (LAYOUT_STATIC_IMPORTS_SEPARATELY) {
      // add <all other static imports> entry if there is none
      boolean found = false;
      for (PackageEntry entry : IMPORT_LAYOUT_TABLE.getEntries()) {
        if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
          found = true;
          break;
        }
      }
      if (!found) {
        PackageEntry last = IMPORT_LAYOUT_TABLE.getEntryCount() == 0 ? null : IMPORT_LAYOUT_TABLE.getEntryAt(IMPORT_LAYOUT_TABLE.getEntryCount() - 1);
        if (last != PackageEntry.BLANK_LINE_ENTRY) {
          IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        }
        IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      }
    }

    myRepeatAnnotations.clear();
    Element annotations = element.getChild(REPEAT_ANNOTATIONS);
    if (annotations != null) {
      for (Element anno : annotations.getChildren("ANNO")) {
        myRepeatAnnotations.add(anno.getAttributeValue("name"));
      }
    }

    myCustomCodeStyleSettingsManager.readExternal(element);

    List<Element> list = element.getChildren(ADDITIONAL_INDENT_OPTIONS);
    for (Element additionalIndentElement : list) {
      String fileTypeId = additionalIndentElement.getAttributeValue(FILETYPE);
      if (!StringUtil.isEmpty(fileTypeId)) {
        FileType target = FileTypeRegistry.getInstance().getFileTypeByExtension(fileTypeId);
        if (UnknownFileType.INSTANCE == target || target instanceof PlainTextLikeFileType || target.getDefaultExtension().isEmpty()) {
          target = new TempFileType(fileTypeId);
        }

        IndentOptions options = getDefaultIndentOptions(target);
        options.readExternal(additionalIndentElement);
        registerAdditionalIndentOptions(target, options);
      }
    }

    myCommonSettingsManager.readExternal(element);


    mySoftMargins.deserializeFrom(element);
    myExcludedFiles.deserializeFrom(element);

    migrateLegacySettings();
    myCustomCodeStyleSettingsManager.notifySettingsLoaded();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    setVersion(element, myVersion);
    CodeStyleSettings defaultSettings = new CodeStyleSettings(true, false);
    DefaultJDOMExternalizer.write(this, element, new DifferenceFilter<>(this, defaultSettings));
    mySoftMargins.serializeInto(element);
    myExcludedFiles.serializeInto(element);

    myCustomCodeStyleSettingsManager.writeExternal(element, defaultSettings);

    if (!myAdditionalIndentOptions.isEmpty()) {
      FileType[] fileTypes = myAdditionalIndentOptions.keySet().toArray(FileType.EMPTY_ARRAY);
      Arrays.sort(fileTypes, Comparator.comparing(FileType::getDefaultExtension));
      for (FileType fileType : fileTypes) {
        Element additionalIndentOptions = new Element(ADDITIONAL_INDENT_OPTIONS);
        myAdditionalIndentOptions.get(fileType).serialize(additionalIndentOptions, getDefaultIndentOptions(fileType));
        additionalIndentOptions.setAttribute(FILETYPE, fileType.getDefaultExtension());
        if (!additionalIndentOptions.getChildren().isEmpty()) {
          element.addContent(additionalIndentOptions);
        }
      }
    }

    myCommonSettingsManager.writeExternal(element);
    if (!myRepeatAnnotations.isEmpty()) {
      Element annos = new Element(REPEAT_ANNOTATIONS);
      for (String annotation : myRepeatAnnotations) {
        annos.addContent(new Element("ANNO").setAttribute("name", annotation));
      }
      element.addContent(annos);
    }
  }

  private static IndentOptions getDefaultIndentOptions(FileType fileType) {
    for (final FileTypeIndentOptionsFactory factory : CodeStyleSettingsService.getInstance().getFileTypeIndentOptionsFactories()) {
      if (factory.getFileType().equals(fileType)) {
        return getFileTypeIndentOptions(factory);
      }
    }
    return new IndentOptions();
  }

  @Override
  @NotNull
  public IndentOptions getIndentOptions() {
    return OTHER_INDENT_OPTIONS;
  }

  /**
   * If the file type has an associated language and language indent options are defined, returns these options. Otherwise attempts to find
   * indent options from {@code FileTypeIndentOptionsProvider}. If none are found, other indent options are returned.
   * @param fileType The file type to search indent options for.
   * @return File type indent options or {@code OTHER_INDENT_OPTIONS}.
   *
   * @see FileTypeIndentOptionsProvider
   * @see LanguageCodeStyleSettingsProvider
   */
  @NotNull
  public IndentOptions getIndentOptions(@Nullable FileType fileType) {
    IndentOptions indentOptions = getLanguageIndentOptions(fileType);
    if (indentOptions != null) return indentOptions;

    if (fileType == null) return OTHER_INDENT_OPTIONS;

    if (!myLoadedAdditionalIndentOptions) {
      loadAdditionalIndentOptions();
    }
    indentOptions = myAdditionalIndentOptions.get(fileType);
    if (indentOptions != null) return indentOptions;

    return OTHER_INDENT_OPTIONS;
  }

  /**
   * If the document has an associated PsiFile, returns options for this file. Otherwise attempts to find associated VirtualFile and
   * return options for corresponding FileType. If none are found, other indent options are returned.
   *
   * @param project  The project in which PsiFile should be searched.
   * @param document The document to search indent options for.
   * @return Indent options from the indent options providers or file type indent options or {@code OTHER_INDENT_OPTIONS}.
   * @see FileIndentOptionsProvider
   * @see FileTypeIndentOptionsProvider
   * @see LanguageCodeStyleSettingsProvider
   */
  @NotNull
  public IndentOptions getIndentOptionsByDocument(@Nullable Project project, @NotNull Document document) {
    PsiFile file = project != null ? PsiDocumentManager.getInstance(project).getPsiFile(document) : null;
    if (file != null) return getIndentOptionsByFile(file);

    VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    FileType fileType = vFile != null ? vFile.getFileType() : null;
    return getIndentOptions(fileType);
  }

  @NotNull
  public IndentOptions getIndentOptionsByFile(@Nullable PsiFile file) {
    return getIndentOptionsByFile(file, null);
  }

  @NotNull
  public IndentOptions getIndentOptionsByFile(@Nullable PsiFile file, @Nullable TextRange formatRange) {
    return getIndentOptionsByFile(file, formatRange, false, null);
  }

  /**
   * Retrieves indent options for PSI file from an associated document or (if not defined in the document) from file indent options
   * providers.
   * @param file  The PSI file to retrieve options for.
   * @param formatRange The text range within the file for formatting purposes or null if there is either no specific range or multiple
   *                    ranges. If the range covers the entire file (full reformat), options stored in the document are ignored and
   *                    indent options are taken from file indent options providers.
   * @param ignoreDocOptions Ignore options stored in the document and use file indent options providers even if there is no text range
   *                         or the text range doesn't cover the entire file.
   * @param providerProcessor A callback object containing a reference to indent option provider which has returned indent options.
   * @return Indent options from the associated document or file indent options providers.
   * @see FileIndentOptionsProvider
   */
  @NotNull
  public IndentOptions getIndentOptionsByFile(@Nullable PsiFile file, @Nullable TextRange formatRange, boolean ignoreDocOptions,
                                              @Nullable Processor<? super FileIndentOptionsProvider> providerProcessor) {
    if (file != null && file.isValid()) {
      boolean isFullReformat = isFileFullyCoveredByRange(file, formatRange);
      if (!ignoreDocOptions && !isFullReformat) {
        IndentOptions options = IndentOptions.retrieveFromAssociatedDocument(file);
        if (options != null) {
          FileIndentOptionsProvider provider = options.getFileIndentOptionsProvider();
          if (providerProcessor != null && provider != null) {
            providerProcessor.process(provider);
          }
          return options;
        }
      }

      for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensionList()) {
        if (!isFullReformat || provider.useOnFullReformat()) {
          IndentOptions indentOptions = provider.getIndentOptions(this, file);
          if (indentOptions != null) {
            if (providerProcessor != null) {
              providerProcessor.process(provider);
            }
            indentOptions.setFileIndentOptionsProvider(provider);
            logIndentOptions(file, provider, indentOptions);
            return indentOptions;
          }
        }
      }

      Language language = LanguageUtil.getLanguageForPsi(file.getProject(), file.getVirtualFile(), file.getFileType());
      if (language != null) {
        IndentOptions options = getIndentOptions(language);
        if (options != null) {
          return options;
        }
      }

      return getIndentOptions(file.getFileType());
    }
    else
      return OTHER_INDENT_OPTIONS;
  }

  private static boolean isFileFullyCoveredByRange(@NotNull PsiFile file, @Nullable TextRange formatRange) {
    return formatRange != null && formatRange.equals(file.getTextRange());
  }

  private static void logIndentOptions(@NotNull PsiFile file,
                                       @NotNull FileIndentOptionsProvider provider,
                                       @NotNull IndentOptions options) {
    LOG.debug("Indent options returned by " + provider.getClass().getName() +
              " for " + file.getName() +
              ": indent size=" + options.INDENT_SIZE +
              ", use tabs=" + options.USE_TAB_CHARACTER +
              ", tab size=" + options.TAB_SIZE);
  }

  @Nullable
  private IndentOptions getLanguageIndentOptions(@Nullable FileType fileType) {
    if (!(fileType instanceof LanguageFileType)) return null;
    Language lang = ((LanguageFileType)fileType).getLanguage();
    return getIndentOptions(lang);
  }

  /**
   * Returns language indent options or, if the language doesn't have any options of its own, indent options configured for other file
   * types.
   *
   * @param language The language to get indent options for.
   * @return Language indent options.
   */
  public IndentOptions getLanguageIndentOptions(@NotNull Language language) {
    IndentOptions langOptions = getIndentOptions(language);
    return langOptions != null ? langOptions : OTHER_INDENT_OPTIONS;
  }

  @Nullable
  private IndentOptions getIndentOptions(Language lang) {
    CommonCodeStyleSettings settings = myCommonSettingsManager.getCommonSettings(lang);
    return settings != null ? settings.getIndentOptions() : null;
  }

  public boolean isSmartTabs(FileType fileType) {
    return getIndentOptions(fileType).SMART_TABS;
  }

  public int getIndentSize(FileType fileType) {
    return getIndentOptions(fileType).INDENT_SIZE;
  }

  public int getContinuationIndentSize(FileType fileType) {
    return getIndentOptions(fileType).CONTINUATION_INDENT_SIZE;
  }

  public int getTabSize(FileType fileType) {
    return getIndentOptions(fileType).TAB_SIZE;
  }

  public boolean useTabCharacter(FileType fileType) {
    return getIndentOptions(fileType).USE_TAB_CHARACTER;
  }

  void registerAdditionalIndentOptions(FileType fileType, IndentOptions options) {
    FileType registered = findRegisteredFileType(fileType);
    if (registered == null || registered instanceof TempFileType) {
      myAdditionalIndentOptions.put(fileType, options);
    }
  }

  @Nullable
  private FileType findRegisteredFileType(@NotNull FileType provided) {
    for (final FileType existing : myAdditionalIndentOptions.keySet()) {
      if (Comparing.strEqual(existing.getDefaultExtension(), provided.getDefaultExtension())) {
        return existing;
      }
    }
    return null;
  }

  private void loadAdditionalIndentOptions() {
    synchronized (myAdditionalIndentOptions) {
      myLoadedAdditionalIndentOptions = true;
      for (final FileTypeIndentOptionsFactory factory : CodeStyleSettingsService.getInstance().getFileTypeIndentOptionsFactories()) {
        if (!myAdditionalIndentOptions.containsKey(factory.getFileType())) {
          registerAdditionalIndentOptions(factory.getFileType(), getFileTypeIndentOptions(factory));
        }
      }
    }
  }

  void unregisterAdditionalIndentOptions(@NotNull FileType fileType) {
    FileType registered = findRegisteredFileType(fileType);
    if (registered != null && !(registered instanceof TempFileType)) {
      FileType tempFileType = new TempFileType(fileType.getDefaultExtension());
      IndentOptions indentOptions = myAdditionalIndentOptions.get(fileType);
      myAdditionalIndentOptions.remove(fileType);
      myAdditionalIndentOptions.put(tempFileType, indentOptions);
    }
  }

  private static IndentOptions getFileTypeIndentOptions(@NotNull FileTypeIndentOptionsFactory factory) {
    try {
      return factory.createIndentOptions();
    }
    catch (AbstractMethodError error) {
      LOG.error(PluginException.createByClass("Plugin uses obsolete API", null, factory.getClass()));
      return new IndentOptions();
    }
  }

  @TestOnly
  public void clearCodeStyleSettings() {
    CodeStyleSettings cleanSettings = new CodeStyleSettings(true, false);
    copyFrom(cleanSettings);
    myAdditionalIndentOptions.clear(); //hack
    myLoadedAdditionalIndentOptions = false;
  }

  private static final class TempFileType implements FileType {
    private final String myExtension;

    private TempFileType(@NotNull final String extension) {
      myExtension = extension;
    }

    @Override
    @NotNull
    public String getName() {
      return "TempFileType";
    }

    @Override
    @NotNull
    public @NonNls String getDescription() {
      return "TempFileType";
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
      return myExtension;
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public boolean isBinary() {
      return false;
    }
  }

  /**
   * Attempts to get language-specific common settings from {@code LanguageCodeStyleSettingsProvider}.
   *
   * @param lang The language to get settings for.
   * @return If the provider for the language exists and is able to create language-specific default settings
   *         ({@code LanguageCodeStyleSettingsProvider.getDefaultCommonSettings()} doesn't return null)
   *         returns the instance of settings for this language. Otherwise returns new instance of common code style settings
   *         with default values.
   */
  @NotNull
  public CommonCodeStyleSettings getCommonSettings(@Nullable Language lang) {
    CommonCodeStyleSettings settings = myCommonSettingsManager.getCommonSettings(lang);
    if (settings == null) {
      settings = myCommonSettingsManager.getDefaults();
      //if (lang != null) {
      //  LOG.warn("Common code style settings for language '" + lang.getDisplayName() + "' not found, using defaults.");
      //}
    }
    return settings;
  }

  /**
   * @param langName The language name.
   * @return Language-specific code style settings or shared settings if not found.
   * @see CommonCodeStyleSettingsManager#getCommonSettings
   */
  public CommonCodeStyleSettings getCommonSettings(String langName) {
    return myCommonSettingsManager.getCommonSettings(langName);
  }

  /**
   * Retrieves right margin for the given language. The language may overwrite default RIGHT_MARGIN value with its own RIGHT_MARGIN
   * in language's CommonCodeStyleSettings instance.
   *
   * @param language The language to get right margin for or null if root (default) right margin is requested.
   * @return The right margin for the language if it is defined (not null) and its settings contain non-negative margin. Root (default)
   *         margin otherwise (CodeStyleSettings.RIGHT_MARGIN).
   */
  public int getRightMargin(@Nullable Language language) {
    if (language != null) {
      CommonCodeStyleSettings langSettings = myCommonSettingsManager.getCommonSettings(language);
      if (langSettings != null) {
        if (langSettings.RIGHT_MARGIN >= 0) return langSettings.RIGHT_MARGIN;
      }
    }
    return getDefaultRightMargin();
  }

  /**
   * Assigns another right margin for the language or (if it is null) to root (default) margin.
   *
   * @param language The language to assign the right margin to or null if root (default) right margin is to be changed.
   * @param rightMargin New right margin.
   */
  public void setRightMargin(@Nullable Language language, int rightMargin) {
    if (language != null) {
      CommonCodeStyleSettings langSettings = myCommonSettingsManager.getCommonSettings(language);
      if (langSettings != null) {
        langSettings.RIGHT_MARGIN = rightMargin;
        return;
      }
    }
    setDefaultRightMargin(rightMargin);
  }

  /**
   * @return The globally set right margin (hard wrap boundary) as configured at the top of Settings|Editor|Code Style. To get the
   * actual right margin in effect call {@link #getRightMargin(Language)}
   */
  public int getDefaultRightMargin() {
    return RIGHT_MARGIN;
  }

  /**
   * Set the global right margin (hard wrap boundary) which will be used for any language which doesn't define its own margin in
   * {@link CommonCodeStyleSettings#RIGHT_MARGIN}
   *
   * @param rightMargin The new right margin to set.
   *
   * @see #setRightMargin(Language, int)
   */
  public void setDefaultRightMargin(int rightMargin) {
    RIGHT_MARGIN = rightMargin;
  }

  /**
   * Defines whether wrapping should occur when typing reaches right margin.
   * @param language  The language to check the option for or null for a global option.
   * @return True if wrapping on right margin is enabled.
   */
  public boolean isWrapOnTyping(@Nullable Language language) {
    if (language != null) {
      CommonCodeStyleSettings langSettings = myCommonSettingsManager.getCommonSettings(language);
      if (langSettings != null) {
        if (langSettings.WRAP_ON_TYPING != CommonCodeStyleSettings.WrapOnTyping.DEFAULT.intValue) {
          return langSettings.WRAP_ON_TYPING == CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue;
        }
      }
    }
    return WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
  }

  public enum WrapStyle implements PresentableEnum {

    DO_NOT_WRAP(CommonCodeStyleSettings.DO_NOT_WRAP, CodeStyleBundle.messagePointer("wrapping.do.not.wrap")),
    WRAP_AS_NEEDED(CommonCodeStyleSettings.WRAP_AS_NEEDED, CodeStyleBundle.messagePointer("wrapping.wrap.if.long")),
    WRAP_ON_EVERY_ITEM(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM, CodeStyleBundle.messagePointer("wrapping.chop.down.if.long")),
    WRAP_ALWAYS(CommonCodeStyleSettings.WRAP_ALWAYS, CodeStyleBundle.messagePointer("wrapping.wrap.always"));

    private final int myId;
    private final Supplier<@Label String> myDescription;

    WrapStyle(int id, @NotNull Supplier<@Label String> description) {
      myId = id;
      myDescription = description;
    }

    public int getId() {
      return myId;
    }

    @Override public @Label String getPresentableText() {
      return myDescription.get();
    }

    public static @NotNull WrapStyle forWrapping(int wrappingStyleID) {
      for (WrapStyle style: values()) {
        if (style.myId == wrappingStyleID) {
          return style;
        }
      }
      LOG.error("Invalid wrapping option index: " + wrappingStyleID);
      return DO_NOT_WRAP;
    }

    public static int getId(@Nullable WrapStyle wrapStyle) {
      return Optional.ofNullable(wrapStyle).orElse(DO_NOT_WRAP).myId;
    }
  }

  public enum HtmlTagNewLineStyle implements PresentableEnum {
    Never("Never", CodeStyleBundle.messagePointer("html.tag.new.line.never")),
    WhenMultiline("When multiline", CodeStyleBundle.messagePointer("html.tag.new.line.when.multiline"));

    private final String myValue;
    private final Supplier<@Label String> myDescription;

    HtmlTagNewLineStyle(@NotNull String value, @NotNull Supplier<@Label String> description) {
      myValue = value;
      myDescription = description;
    }

    @Override
    public String toString() {
      return myValue;
    }

    @Override public @Label String getPresentableText() {
      return myDescription.get();
    }
  }

  public enum QuoteStyle implements PresentableEnum {
    Single("'", CodeStyleBundle.messagePointer("quote.style.single")),
    Double("\"", CodeStyleBundle.messagePointer("quote.style.double")),
    None("", CodeStyleBundle.messagePointer("quote.style.none"));

    public final String quote;
    private final Supplier<@Label String> myDescription;

    QuoteStyle(@NotNull String quote, @NotNull Supplier<@Label String> description) {
      this.quote = quote;
      myDescription = description;
    }

    @Override public @Label String getPresentableText() {
     return myDescription.get();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CodeStyleSettings)) return false;
    if (!ReflectionUtil.comparePublicNonFinalFields(this, obj)) return false;
    if (!mySoftMargins.equals(((CodeStyleSettings)obj).mySoftMargins)) return false;
    if (!myExcludedFiles.equals(((CodeStyleSettings)obj).getExcludedFiles())) return false;
    if (!OTHER_INDENT_OPTIONS.equals(((CodeStyleSettings)obj).OTHER_INDENT_OPTIONS)) return false;
    if (!myCommonSettingsManager.equals(((CodeStyleSettings)obj).myCommonSettingsManager)) return false;
    for (CustomCodeStyleSettings customSettings : myCustomCodeStyleSettingsManager.getAllSettings()) {
      if (!customSettings.equals(((CodeStyleSettings)obj).getCustomSettings(customSettings.getClass()))) return false;
    }
    return true;
  }

  @NotNull
  public static CodeStyleSettings getDefaults() {
    return DefaultsHolder.myDefaults;
  }

  private void migrateLegacySettings() {
    if (myVersion < CURR_VERSION) {
      for (CustomCodeStyleSettings settings : myCustomCodeStyleSettingsManager.getAllSettings()) {
        settings.importLegacySettings(this);
      }
      myVersion = CURR_VERSION;
    }
  }


  public void resetDeprecatedFields() {
    CodeStyleSettings defaults = getDefaults();
    ReflectionUtil.copyFields(getClass().getFields(), defaults, this, new DifferenceFilter<>(this, defaults){
      @Override
      public boolean test(@NotNull Field field) {
        return field.getAnnotation(Deprecated.class) != null;
      }
    });
    IMPORT_LAYOUT_TABLE.copyFrom(defaults.IMPORT_LAYOUT_TABLE);
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(defaults.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    myRepeatAnnotations.clear();
  }

  public int getVersion() {
    return myVersion;
  }

  /**
   * Returns soft margins (visual indent guides positions) for the language. If language settings do not exists or language soft margins are
   * empty, default (root) soft margins are returned.
   * @param language The language to retrieve soft margins for or {@code null} for default soft margins.
   * @return Language or default soft margins.
   * @see #getDefaultSoftMargins()
   */
  @NotNull
  public List<Integer> getSoftMargins(@Nullable Language language) {
    if (language != null) {
      CommonCodeStyleSettings languageSettings = myCommonSettingsManager.getCommonSettings(language);
      if (languageSettings != null && !languageSettings.getSoftMargins().isEmpty()) {
        return languageSettings.getSoftMargins();
      }
    }
    return getDefaultSoftMargins();
  }

  /**
   * Set soft margins (visual indent guides) for the language. Note: language code style settings must exist.
   * @param language The language to set soft margins for.
   * @param softMargins The soft margins to set.
   */
  public void setSoftMargins(@NotNull Language language, List<Integer> softMargins) {
    CommonCodeStyleSettings languageSettings = myCommonSettingsManager.getCommonSettings(language);
    assert languageSettings != null : "Settings for language " + language.getDisplayName() + " do not exist";
    languageSettings.setSoftMargins(softMargins);
  }

  /**
   * @return Default (root) soft margins used for languages not defining them explicitly.
   */
  @NotNull
  public List<Integer> getDefaultSoftMargins() {
    return mySoftMargins.getValues();
  }

  /**
   * Sets the default soft margins used for languages not defining them explicitly.
   * @param softMargins The default soft margins.
   */
  public void setDefaultSoftMargins(List<Integer> softMargins) {
    mySoftMargins.setValues(softMargins);
  }

  @NotNull
  public ExcludedFiles getExcludedFiles() {
    return myExcludedFiles;
  }

  public SimpleModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @ApiStatus.Internal
  public void removeCommonSettings(@NotNull LanguageCodeStyleProvider provider) {
    myCommonSettingsManager.removeLanguageSettings(provider);
  }

  @ApiStatus.Internal
  public void registerCommonSettings(@NotNull LanguageCodeStyleProvider provider) {
    myCommonSettingsManager.addLanguageSettings(provider);
  }

  @ApiStatus.Internal
  public void removeCustomSettings(@NotNull CustomCodeStyleSettingsFactory factory) {
    myCustomCodeStyleSettingsManager.unregisterCustomSettings(factory);
  }

  @ApiStatus.Internal
  public void registerCustomSettings(@NotNull CustomCodeStyleSettingsFactory factory) {
    myCustomCodeStyleSettingsManager.registerCustomSettings(this, factory);
  }

  CustomCodeStyleSettingsManager getCustomCodeStyleSettingsManager() {
    return myCustomCodeStyleSettingsManager;
  }

}
