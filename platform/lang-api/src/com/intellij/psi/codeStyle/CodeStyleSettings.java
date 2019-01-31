// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.configurationStore.Property;
import com.intellij.configurationStore.UnknownElementCollector;
import com.intellij.configurationStore.UnknownElementWriter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <p>
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
@SuppressWarnings("deprecation")
public class CodeStyleSettings extends LegacyCodeStyleSettings implements Cloneable, JDOMExternalizable, ImportsLayoutSettings {
  public static final int CURR_VERSION = 173;

  private static final Logger LOG = Logger.getInstance(CodeStyleSettings.class);
  public static final String VERSION_ATTR = "version";

  private final ClassMap<CustomCodeStyleSettings> myCustomSettings = new ClassMap<>();

  @NonNls private static final String REPEAT_ANNOTATIONS = "REPEAT_ANNOTATIONS";
  @NonNls private static final String ADDITIONAL_INDENT_OPTIONS = "ADDITIONAL_INDENT_OPTIONS";

  @NonNls private static final String FILETYPE = "fileType";
  private CommonCodeStyleSettingsManager myCommonSettingsManager = new CommonCodeStyleSettingsManager(this);

  private static class DefaultsHolder {
    private static final CodeStyleSettings myDefaults = new CodeStyleSettings();
  }

  private UnknownElementWriter myUnknownElementWriter = UnknownElementWriter.EMPTY;

  private final SoftMargins mySoftMargins = new SoftMargins();

  private final ExcludedFiles myExcludedFiles = new ExcludedFiles();

  private int myVersion = CURR_VERSION;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public CodeStyleSettings() {
    this(true);
  }

  public CodeStyleSettings(boolean loadExtensions) {
    initTypeToName();
    initImportsByDefault();

    if (loadExtensions) {
      for (final CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList()) {
        addCustomSettings(provider.createCustomSettings(this));
      }
      for (CodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getSettingsPagesProviders()) {
        addCustomSettings(provider.createCustomSettings(this));
      }
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

  private void initTypeToName() {
    initGeneralLocalVariable(PARAMETER_TYPE_TO_NAME);
    initGeneralLocalVariable(LOCAL_VARIABLE_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.addPair("*Exception", "e");
  }

  private static void initGeneralLocalVariable(@NonNls TypeToNameMap map) {
    map.addPair("int", "i");
    map.addPair("byte", "b");
    map.addPair("char", "c");
    map.addPair("long", "l");
    map.addPair("short", "i");
    map.addPair("boolean", "b");
    map.addPair("double", "v");
    map.addPair("float", "v");
    map.addPair("java.lang.Object", "o");
    map.addPair("java.lang.String", "s");
  }

  public void setParentSettings(CodeStyleSettings parent) {
    myParentSettings = parent;
  }

  public CodeStyleSettings getParentSettings() {
    return myParentSettings;
  }

  private void addCustomSettings(CustomCodeStyleSettings settings) {
    if (settings != null) {
      synchronized (myCustomSettings) {
        myCustomSettings.put(settings.getClass(), settings);
      }
    }
  }

  @NotNull
  public <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> aClass) {
    synchronized (myCustomSettings) {
      //noinspection unchecked
      T result = (T)myCustomSettings.get(aClass);
      if (result == null) {
        throw new RuntimeException("Unable to get registered settings of #" + aClass.getSimpleName() + " (" + aClass.getName() + ")");
      }
      return result;
    }
  }

  @Override
  public CodeStyleSettings clone() {
    CodeStyleSettings clone = new CodeStyleSettings();
    clone.copyFrom(this);
    return clone;
  }

  private void copyCustomSettingsFrom(@NotNull CodeStyleSettings from) {
    synchronized (myCustomSettings) {
      myCustomSettings.clear();

      for (final CustomCodeStyleSettings settings : from.getCustomSettingsValues()) {
        addCustomSettings((CustomCodeStyleSettings)settings.clone());
      }

      FIELD_TYPE_TO_NAME.copyFrom(from.FIELD_TYPE_TO_NAME);
      STATIC_FIELD_TYPE_TO_NAME.copyFrom(from.STATIC_FIELD_TYPE_TO_NAME);
      PARAMETER_TYPE_TO_NAME.copyFrom(from.PARAMETER_TYPE_TO_NAME);
      LOCAL_VARIABLE_TYPE_TO_NAME.copyFrom(from.LOCAL_VARIABLE_TYPE_TO_NAME);

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
  }

  public void copyFrom(CodeStyleSettings from) {
    CommonCodeStyleSettings.copyPublicFields(from, this);
    CommonCodeStyleSettings.copyPublicFields(from.OTHER_INDENT_OPTIONS, OTHER_INDENT_OPTIONS);
    mySoftMargins.setValues(from.getDefaultSoftMargins());
    myExcludedFiles.setDescriptors(from.getExcludedFiles().getDescriptors());
    copyCustomSettingsFrom(from);
  }


  public boolean USE_SAME_INDENTS;

  public boolean IGNORE_SAME_INDENTS_FOR_LANGUAGES;

  public boolean AUTODETECT_INDENTS = true;

  public final IndentOptions OTHER_INDENT_OPTIONS = new IndentOptions();

  private final Map<FileType,IndentOptions> myAdditionalIndentOptions = new LinkedHashMap<>();

  private static final String ourSystemLineSeparator = SystemProperties.getLineSeparator();

  /**
   * Line separator. It can be null if choosen line separator is "System-dependent"!
   */
  public String LINE_SEPARATOR;

  /**
   * @return line separator. If choosen line separator is "System-dependent" method returns default separator for this OS.
   */
  public String getLineSeparator() {
    return LINE_SEPARATOR != null ? LINE_SEPARATOR : ourSystemLineSeparator;
  }


// region Java settings (legacy)
//----------------- NAMING CONVENTIONS --------------------

  /** @deprecated Use JavaCodeStyleSettings.FIELD_NAME_PREFIX */
  @Deprecated
  public String FIELD_NAME_PREFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.STATIC_FIELD_NAME_PREFIX */
  @Deprecated
  public String STATIC_FIELD_NAME_PREFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.PARAMETER_NAME_PREFIX */
  @Deprecated
  public String PARAMETER_NAME_PREFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.LOCAL_VARIABL_NAME_PREFIX */
  @Deprecated
  public String LOCAL_VARIABLE_NAME_PREFIX = "";

  /** @deprecated Use JavaCodeStyleSettings.FIELD_NAME_SUFFIX */
  @Deprecated
  public String FIELD_NAME_SUFFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.STATIC_FIELD_NAME_SUFFIX */
  @Deprecated
  public String STATIC_FIELD_NAME_SUFFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.PARAMETER_NAME_SUFFIX */
  @Deprecated
  public String PARAMETER_NAME_SUFFIX = "";
  /** @deprecated Use JavaCodeStyleSettings.LOCAL_VARIABLE_NAME_SUFFIX */
  @Deprecated
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";

  /** @deprecated Use JavaCodeStyleSettings.PREFER_LONGER_NAMES */
  @Deprecated
  public boolean PREFER_LONGER_NAMES = true;

  /** @deprecated Use JavaCodeStyleSettings.FILED_TYPE_TO_NAME */
  @Deprecated
  public final TypeToNameMap FIELD_TYPE_TO_NAME = new TypeToNameMap();
  /** @deprecated Use JavaCodeStyleSettings.STATIC_FIELD_TYPE_TO_NAME */
  @Deprecated
  public final TypeToNameMap STATIC_FIELD_TYPE_TO_NAME = new TypeToNameMap();
  /** @deprecated Use JavaCodeStyleSettings.PARAMETER_TYPE_TO_NAME */
  @Deprecated
  @NonNls public final TypeToNameMap PARAMETER_TYPE_TO_NAME = new TypeToNameMap();
  /** @deprecated Use JavaCodeStyleSettings.LOCAL_VARIABLE_TYPE_TO_NAME */
  @Deprecated
  public final TypeToNameMap LOCAL_VARIABLE_TYPE_TO_NAME = new TypeToNameMap();

//----------------- 'final' modifier settings -------
  /** @deprecated Use JavaCodeStyleSettings.GENERATE_FINAL_LOCALS */
  @Deprecated
  public boolean GENERATE_FINAL_LOCALS;
  /** @deprecated Use JavaCodeStyleSettings.GENERATE_FINAL_PARAMETERS */
  @Deprecated
  public boolean GENERATE_FINAL_PARAMETERS;

//----------------- visibility -----------------------------
  /** @deprecated Use JavaCodeStyleSettings.VISIBILITY */
  @Deprecated
  public String VISIBILITY = "public";

//----------------- generate parentheses around method arguments ----------
  /** @deprecated Use RubyCodeStyleSettings.PARENTHESES_AROUND_METHOD_ARGUMENTS */
  @Deprecated
  public boolean PARENTHESES_AROUND_METHOD_ARGUMENTS = true;

//----------------- annotations ----------------
  /** @deprecated Use JavaCodeStyleSettings.USE_EXTERNAL_ANNOTATIONS */
  @Deprecated
  public boolean USE_EXTERNAL_ANNOTATIONS;
  /** @deprecated Use JavaCodeStyleSettings.INSERT_OVERRIDE_ANNOTATIONS */
  @Deprecated
  public boolean INSERT_OVERRIDE_ANNOTATION = true;

//----------------- override -------------------
  /** @deprecated Use JavaCodeStyleSettings.REPEAT_SYNCHRONIZED */
  @Deprecated
  public boolean REPEAT_SYNCHRONIZED = true;

  private final List<String> myRepeatAnnotations = new ArrayList<>();

  /** @deprecated Use JavaCodeStyleSettings.getRepeatAnnotations() */
  @Deprecated
  public List<String> getRepeatAnnotations() {
    return myRepeatAnnotations;
  }

  /** @deprecated Use JavaCodeStyleSettings.setRepeatAnnotations() */
  @Deprecated
  public void setRepeatAnnotations(List<String> repeatAnnotations) {
    myRepeatAnnotations.clear();
    myRepeatAnnotations.addAll(repeatAnnotations);
  }

  //----------------- FUNCTIONAL EXPRESSIONS -----

  /** @deprecated Use JavaCodeStyleSettings.REPLACE_INSTANCEOF_AND_CAST */
  @Deprecated
  public boolean REPLACE_INSTANCEOF;
  /** @deprecated Use JavaCodeStyleSettings.REPLACE_INSTANCEOF_AND_CAST */
  @Deprecated
  public boolean REPLACE_CAST;
  /** @deprecated Use JavaCodeStyleSettings.REPLACE_NULL_CHECK */
  @Deprecated
  public boolean REPLACE_NULL_CHECK = true;


  //----------------- JAVA IMPORTS (deprecated, moved to JavaCodeStyleSettings) --------------------

  /** @deprecated Use JavaCodeStyleSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;

  /** @deprecated Use JavaCodeStyleSettings.USE_FQ_CLASS_NAMES */
  @Deprecated
  public boolean USE_FQ_CLASS_NAMES;

  /** @deprecated use com.intellij.psi.codeStyle.JavaCodeStyleSettings.CLASS_NAMES_IN_JAVADOC */
  @Deprecated
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;

  /** @deprecated Use JavaCodeStyleSettings.USE_SINGLE_CLASS_IMPORTS */
  @Deprecated
  public boolean USE_SINGLE_CLASS_IMPORTS = true;

  /** @deprecated Use JavaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS */
  @Deprecated
  public boolean INSERT_INNER_CLASS_IMPORTS;

  /** @deprecated Use JavaCodeStyleSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND */
  @Deprecated
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;

  /** @deprecated Use JavaCodeStyleSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND */
  @Deprecated
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;

  /** @deprecated Use JavaCodeStyleSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();

  /** @deprecated Use JavaCodeStyleSettings.IMPORT_LAYOUT_TABLE */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();

  @Override
  @Deprecated
  public boolean isLayoutStaticImportsSeparately() {
    return LAYOUT_STATIC_IMPORTS_SEPARATELY;
  }

  @Override
  @Deprecated
  public void setLayoutStaticImportsSeparately(boolean value) {
    LAYOUT_STATIC_IMPORTS_SEPARATELY = value;
  }

  @Deprecated
  @Override
  public int getNamesCountToUseImportOnDemand() {
    return NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Deprecated
  @Override
  public void setNamesCountToUseImportOnDemand(int value) {
    NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Deprecated
  @Override
  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Deprecated
  @Override
  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Deprecated
  @Override
  public boolean isInsertInnerClassImports() {
    return INSERT_INNER_CLASS_IMPORTS;
  }

  @Deprecated
  @Override
  public void setInsertInnerClassImports(boolean value) {
    INSERT_INNER_CLASS_IMPORTS = value;
  }

  @Deprecated
  @Override
  public boolean isUseSingleClassImports() {
    return USE_SINGLE_CLASS_IMPORTS;
  }

  @Deprecated
  @Override
  public void setUseSingleClassImports(boolean value) {
    USE_SINGLE_CLASS_IMPORTS = value;
  }

  @Deprecated
  @Override
  public boolean isUseFqClassNames() {
    return USE_FQ_CLASS_NAMES;
  }

  @Deprecated
  @Override
  public void setUseFqClassNames(boolean value) {
    USE_FQ_CLASS_NAMES = value;
  }

  @Deprecated
  @Override
  public PackageEntryTable getImportLayoutTable() {
    return IMPORT_LAYOUT_TABLE;
  }

  @Deprecated
  @Override
  public PackageEntryTable getPackagesToUseImportOnDemand() {
    return PACKAGES_TO_USE_IMPORT_ON_DEMAND;
  }

  // endregion

// region ORDER OF MEMBERS

  @Deprecated public int STATIC_FIELDS_ORDER_WEIGHT = 1;
  @Deprecated public int FIELDS_ORDER_WEIGHT = 2;
  @Deprecated public int CONSTRUCTORS_ORDER_WEIGHT = 3;
  @Deprecated public int STATIC_METHODS_ORDER_WEIGHT = 4;
  @Deprecated public int METHODS_ORDER_WEIGHT = 5;
  @Deprecated public int STATIC_INNER_CLASSES_ORDER_WEIGHT = 6;
  @Deprecated public int INNER_CLASSES_ORDER_WEIGHT = 7;

// endregion

// region WRAPPING
  /**
   * @deprecated Use get/setRightMargin() methods instead.
   */
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
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
   * @deprecated  Use JavaCodeStyleSettings.ENABLE_JAVADOC_FORMATTING
   */
  @Deprecated
  public boolean ENABLE_JAVADOC_FORMATTING = true;

  /**
   * Align parameter comments to longest parameter name.JD_ALIGN_PARAM_COMMENTS
   * @deprecated Use JavaCodeStyleSettings.JD_ALIGN_PARAM_COMMENTS
   */
  @Deprecated
  public boolean JD_ALIGN_PARAM_COMMENTS = true;

  /**
   * Align exception comments to longest exception name
   * @deprecated Use JavaCodeStyleSettings.JD_ALIGN_EXCEPTION_COMMENTS
   */
  @Deprecated
  public boolean JD_ALIGN_EXCEPTION_COMMENTS = true;

  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_ADD_BLANK_AFTER_PARM_COMMENTS;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_ADD_BLANK_AFTER_RETURN;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_ADD_BLANK_AFTER_DESCRIPTION = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_P_AT_EMPTY_LINES = true;

  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_KEEP_INVALID_TAGS = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_KEEP_EMPTY_LINES = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_DO_NOT_WRAP_ONE_LINE_COMMENTS;

  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_USE_THROWS_NOT_EXCEPTION = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_KEEP_EMPTY_PARAMETER = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_KEEP_EMPTY_EXCEPTION = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.
   */
  @Deprecated
  public boolean JD_KEEP_EMPTY_RETURN = true;


  /**
   * @deprecated Use JavaCodeStyleSettings.JD_LEADING_ASTERISKS_ARE_ENABLED
   */
  @Deprecated
  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;
  /**
   * @deprecated Use JavaCodeStyleSettings.JD_PRESERVE_LINE_FEEDS
   */
  @Deprecated
  public boolean JD_PRESERVE_LINE_FEEDS;
  /**
   * @deprecated Use JavaCodeStyleSettings.JD_PARAM_DESCRIPTION_ON_NEW_LINE
   */
  @Deprecated
  public boolean JD_PARAM_DESCRIPTION_ON_NEW_LINE;
  /**
   * @deprecated Use JavaCodeStyleSettings.JD_INDENT_ON_CONTINUATION
   */
  @Deprecated
  public boolean JD_INDENT_ON_CONTINUATION;

// endregion

// region HTML formatting options (legacy)

  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_KEEP_WHITESPACES;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public int HTML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public int HTML_TEXT_WRAP = WRAP_AS_NEEDED;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_KEEP_LINE_BREAKS = true;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_KEEP_LINE_BREAKS_IN_TEXT = true;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public int HTML_KEEP_BLANK_LINES = 2;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_ALIGN_ATTRIBUTES = true;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_ALIGN_TEXT;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_SPACE_AFTER_TAG_NAME;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_SPACE_INSIDE_EMPTY_TAG;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = "body,div,p,form,h1,h2,h3";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = "br";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_DO_NOT_INDENT_CHILDREN_OF = "html,body,thead,tbody,tfoot";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public int HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_KEEP_WHITESPACES_INSIDE = "span,pre,textarea";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_INLINE_ELEMENTS =
    "a,abbr,acronym,b,basefont,bdo,big,br,cite,cite,code,dfn,em,font,i,img,input,kbd,label,q,s,samp,select,span,strike,strong,sub,sup,textarea,tt,u,var";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  @NonNls public String HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = "title,h1,h2,h3,h4,h5,h6,p";
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public QuoteStyle HTML_QUOTE_STYLE = QuoteStyle.Double;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public boolean HTML_ENFORCE_QUOTES;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public HtmlTagNewLineStyle HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = HtmlTagNewLineStyle.Never;
  /**
   * @deprecated Use HtmlCodeStyleSettings
   */
  @Deprecated
  public HtmlTagNewLineStyle HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = HtmlTagNewLineStyle.Never;

// endregion

  @Deprecated
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


  // endregion

  //----------------------------------------------------------------------------------------

  private CodeStyleSettings myParentSettings;
  private boolean myLoadedAdditionalIndentOptions;

  @NotNull
  private Collection<CustomCodeStyleSettings> getCustomSettingsValues() {
    synchronized (myCustomSettings) {
      return Collections.unmodifiableCollection(myCustomSettings.values());
    }
  }

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
    notifySettingsBeforeLoading();
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

    UnknownElementCollector unknownElementCollector = new UnknownElementCollector();
    for (CustomCodeStyleSettings settings : getCustomSettingsValues()) {
      settings.getKnownTagNames().forEach(unknownElementCollector::addKnownName);
      settings.readExternal(element);
    }

    unknownElementCollector.addKnownName(ADDITIONAL_INDENT_OPTIONS);
    List<Element> list = element.getChildren(ADDITIONAL_INDENT_OPTIONS);
    for (Element additionalIndentElement : list) {
      String fileTypeId = additionalIndentElement.getAttributeValue(FILETYPE);
      if (!StringUtil.isEmpty(fileTypeId)) {
        FileType target = FileTypeManager.getInstance().getFileTypeByExtension(fileTypeId);
        if (FileTypes.UNKNOWN == target || FileTypes.PLAIN_TEXT == target || target.getDefaultExtension().isEmpty()) {
          target = new TempFileType(fileTypeId);
        }

        IndentOptions options = getDefaultIndentOptions(target);
        options.readExternal(additionalIndentElement);
        registerAdditionalIndentOptions(target, options);
      }
    }

    unknownElementCollector.addKnownName(CommonCodeStyleSettingsManager.COMMON_SETTINGS_TAG);
    myCommonSettingsManager.readExternal(element);

    myUnknownElementWriter = unknownElementCollector.createWriter(element);

    if (USE_SAME_INDENTS) {
      IGNORE_SAME_INDENTS_FOR_LANGUAGES = true;
    }

    mySoftMargins.deserializeFrom(element);
    myExcludedFiles.deserializeFrom(element);

    migrateLegacySettings();
    notifySettingsLoaded();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    setVersion(element, myVersion);
    CodeStyleSettings parentSettings = new CodeStyleSettings();
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<>(this, parentSettings));
    mySoftMargins.serializeInto(element);
    myExcludedFiles.serializeInto(element);

    myUnknownElementWriter.write(element, getCustomSettingsValues(), CustomCodeStyleSettings::getTagName, settings -> {
      CustomCodeStyleSettings parentCustomSettings = parentSettings.getCustomSettings(settings.getClass());
      settings.writeExternal(element, parentCustomSettings);
    });

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
    for (final FileTypeIndentOptionsProvider provider : FileTypeIndentOptionsProvider.EP_NAME.getExtensionList()) {
      if (provider.getFileType().equals(fileType)) {
        return getFileTypeIndentOptions(provider);
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

    if (USE_SAME_INDENTS || fileType == null) return OTHER_INDENT_OPTIONS;

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

      Language language = LanguageUtil.getLanguageForPsi(file.getProject(), file.getVirtualFile());
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

  public static class TypeToNameMap implements JDOMExternalizable {
    private final List<String> myPatterns = new ArrayList<>();
    private final List<String> myNames = new ArrayList<>();

    public void addPair(String pattern, String name) {
      myPatterns.add(pattern);
      myNames.add(name);
    }

    public String nameByType(String type) {
      for (int i = 0; i < myPatterns.size(); i++) {
        String pattern = myPatterns.get(i);
        if (StringUtil.startsWithChar(pattern, '*')) {
          if (type.endsWith(pattern.substring(1))) {
            return myNames.get(i);
          }
        }
        else {
          if (type.equals(pattern)) {
            return myNames.get(i);
          }
        }
      }
      return null;
    }

    @Override
    public void readExternal(@NonNls Element element) throws InvalidDataException {
      myPatterns.clear();
      myNames.clear();
      for (final Object o : element.getChildren("pair")) {
        @NonNls Element e = (Element)o;

        String pattern = e.getAttributeValue("type");
        String name = e.getAttributeValue("name");
        if (pattern == null || name == null) {
          throw new InvalidDataException();
        }
        myPatterns.add(pattern);
        myNames.add(name);

      }
    }

    @Override
    public void writeExternal(Element parentNode) throws WriteExternalException {
      for (int i = 0; i < myPatterns.size(); i++) {
        String pattern = myPatterns.get(i);
        String name = myNames.get(i);
        @NonNls Element element = new Element("pair");
        parentNode.addContent(element);
        element.setAttribute("type", pattern);
        element.setAttribute("name", name);
      }
    }

    public void copyFrom(TypeToNameMap from) {
      assert from != this;
      myPatterns.clear();
      myPatterns.addAll(from.myPatterns);
      myNames.clear();
      myNames.addAll(from.myNames);
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof TypeToNameMap) {
        TypeToNameMap otherMap = (TypeToNameMap)other;
        return myPatterns.equals(otherMap.myPatterns) && myNames.equals(otherMap.myNames);
      }
      return false;
    }

    @Override
    public int hashCode() {
      int code = 0;
      for (String myPattern : myPatterns) {
        code += myPattern.hashCode();
      }
      for (String myName : myNames) {
        code += myName.hashCode();
      }
      return code;
    }
  }

  private void registerAdditionalIndentOptions(FileType fileType, IndentOptions options) {
    boolean exist = false;
    for (final FileType existing : myAdditionalIndentOptions.keySet()) {
      if (Comparing.strEqual(existing.getDefaultExtension(), fileType.getDefaultExtension())) {
        exist = true;
        break;
      }
    }

    if (!exist) {
      myAdditionalIndentOptions.put(fileType, options);
    }
  }

  private void loadAdditionalIndentOptions() {
    synchronized (myAdditionalIndentOptions) {
      myLoadedAdditionalIndentOptions = true;
      for (final FileTypeIndentOptionsProvider provider : FileTypeIndentOptionsProvider.EP_NAME.getExtensionList()) {
        if (!myAdditionalIndentOptions.containsKey(provider.getFileType())) {
          registerAdditionalIndentOptions(provider.getFileType(), getFileTypeIndentOptions(provider));
        }
      }
    }
  }

  private static IndentOptions getFileTypeIndentOptions(FileTypeIndentOptionsProvider provider) {
    try {
      return provider.createIndentOptions();
    }
    catch (AbstractMethodError error) {
      LOG.error("Plugin uses obsolete API.", new ExtensionException(provider.getClass()));
      return new IndentOptions();
    }
  }

  @TestOnly
  public void clearCodeStyleSettings() {
    CodeStyleSettings cleanSettings = new CodeStyleSettings();
    copyFrom(cleanSettings);
    myAdditionalIndentOptions.clear(); //hack
    myLoadedAdditionalIndentOptions = false;
  }

  private static class TempFileType implements FileType {
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
    public String getDescription() {
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

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
      return null;
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

  @SuppressWarnings("deprecation")
  public int getDefaultRightMargin() {
    return RIGHT_MARGIN;
  }

  @SuppressWarnings("deprecation")
  public void setDefaultRightMargin(int rightMargin) {
    RIGHT_MARGIN = rightMargin;
  }

  /**
   * Defines whether or not wrapping should occur when typing reaches right margin.
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

  public enum HtmlTagNewLineStyle {
    Never("Never"),
    WhenMultiline("When multiline");

    public final String description;

    HtmlTagNewLineStyle(String description) {
      this.description = description;
    }

    @Override
    public String toString() {
      return description;
    }
  }

  public enum QuoteStyle {
    Single("'"),
    Double("\""),
    None("");

    public final String quote;

    QuoteStyle(String quote) {
      this.quote = quote;
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
    for (CustomCodeStyleSettings customSettings : myCustomSettings.values()) {
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
      for (CustomCodeStyleSettings settings : myCustomSettings.values()) {
        settings.importLegacySettings(this);
      }
      myVersion = CURR_VERSION;
    }
  }

  private void notifySettingsBeforeLoading() {
    JBIterable.from(myCustomSettings.values())
              .forEach(CustomCodeStyleSettings::beforeLoading);
  }

  private void notifySettingsLoaded() {
    JBIterable.from(myCustomSettings.values())
              .forEach(CustomCodeStyleSettings::afterLoaded);
  }

  @SuppressWarnings("deprecation")
  public void resetDeprecatedFields() {
    CodeStyleSettings defaults = getDefaults();
    ReflectionUtil.copyFields(getClass().getFields(), defaults, this, new DifferenceFilter<CodeStyleSettings>(this, defaults){
      @Override
      public boolean isAccept(@NotNull Field field) {
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

}
