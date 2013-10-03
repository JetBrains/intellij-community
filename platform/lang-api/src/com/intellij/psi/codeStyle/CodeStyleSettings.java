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
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ClassMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CodeStyleSettings extends CommonCodeStyleSettings implements Cloneable, JDOMExternalizable {
  
  private static final Logger LOG = Logger.getInstance("#" + CodeStyleSettings.class.getName());
  
  private final ClassMap<CustomCodeStyleSettings> myCustomSettings = new ClassMap<CustomCodeStyleSettings>();

  @NonNls private static final String ADDITIONAL_INDENT_OPTIONS = "ADDITIONAL_INDENT_OPTIONS";

  @NonNls private static final String FILETYPE = "fileType";
  private CommonCodeStyleSettingsManager myCommonSettingsManager = new CommonCodeStyleSettingsManager(this);

  public CodeStyleSettings() {
    this(true);
  }

  public CodeStyleSettings(boolean loadExtensions) {
    super(null);
    initTypeToName();
    initImportsByDefault();

    if (loadExtensions) {
      final CodeStyleSettingsProvider[] codeStyleSettingsProviders = Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME);
      for (final CodeStyleSettingsProvider provider : codeStyleSettingsProviders) {
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
      myCustomSettings.put(settings.getClass(), settings);
    }
  }

  public <T extends CustomCodeStyleSettings> T getCustomSettings(Class<T> aClass) {
    return (T)myCustomSettings.get(aClass);
  }

  @Override
  public CodeStyleSettings clone() {
    CodeStyleSettings clone = new CodeStyleSettings();
    clone.copyFrom(this);
    return clone;
  }

  private void copyCustomSettingsFrom(@NotNull CodeStyleSettings from) {
    myCustomSettings.clear();
    for (final CustomCodeStyleSettings settings : from.myCustomSettings.values()) {
      addCustomSettings((CustomCodeStyleSettings) settings.clone());
    }

    FIELD_TYPE_TO_NAME.copyFrom(from.FIELD_TYPE_TO_NAME);
    STATIC_FIELD_TYPE_TO_NAME.copyFrom(from.STATIC_FIELD_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.copyFrom(from.PARAMETER_TYPE_TO_NAME);
    LOCAL_VARIABLE_TYPE_TO_NAME.copyFrom(from.LOCAL_VARIABLE_TYPE_TO_NAME);

    PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(from.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    IMPORT_LAYOUT_TABLE.copyFrom(from.IMPORT_LAYOUT_TABLE);

    OTHER_INDENT_OPTIONS.copyFrom(from.OTHER_INDENT_OPTIONS);

    myAdditionalIndentOptions.clear();
    for(Map.Entry<FileType, IndentOptions> optionEntry: from.myAdditionalIndentOptions.entrySet()) {
      IndentOptions options = optionEntry.getValue();
      myAdditionalIndentOptions.put(optionEntry.getKey(),(IndentOptions)options.clone());
    }
    
    myCommonSettingsManager = from.myCommonSettingsManager.clone(this);
  }

  public void copyFrom(CodeStyleSettings from) {
    copyPublicFields(from, this);

    copyCustomSettingsFrom(from);
  }


  public boolean USE_SAME_INDENTS = false;

  public boolean IGNORE_SAME_INDENTS_FOR_LANGUAGES = false;

  @Deprecated
  public final IndentOptions JAVA_INDENT_OPTIONS = new IndentOptions();
  @Deprecated
  public final IndentOptions JSP_INDENT_OPTIONS = new IndentOptions();
  @Deprecated
  public final IndentOptions XML_INDENT_OPTIONS = new IndentOptions();

  public final IndentOptions OTHER_INDENT_OPTIONS = new IndentOptions();

  private final Map<FileType,IndentOptions> myAdditionalIndentOptions = new LinkedHashMap<FileType, IndentOptions>();

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


//----------------- NAMING CONVENTIONS --------------------

  public String FIELD_NAME_PREFIX = "";
  public String STATIC_FIELD_NAME_PREFIX = "";
  public String PARAMETER_NAME_PREFIX = "";
  public String LOCAL_VARIABLE_NAME_PREFIX = "";

  public String FIELD_NAME_SUFFIX = "";
  public String STATIC_FIELD_NAME_SUFFIX = "";
  public String PARAMETER_NAME_SUFFIX = "";
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";

  public boolean PREFER_LONGER_NAMES = true;

  public final TypeToNameMap FIELD_TYPE_TO_NAME = new TypeToNameMap();
  public final TypeToNameMap STATIC_FIELD_TYPE_TO_NAME = new TypeToNameMap();
  @NonNls public final TypeToNameMap PARAMETER_TYPE_TO_NAME = new TypeToNameMap();
  public final TypeToNameMap LOCAL_VARIABLE_TYPE_TO_NAME = new TypeToNameMap();

//----------------- 'final' modifier settings -------
  public boolean GENERATE_FINAL_LOCALS = false;
  public boolean GENERATE_FINAL_PARAMETERS = false;

//----------------- generate parentheses around method arguments ----------
  public boolean PARENTHESES_AROUND_METHOD_ARGUMENTS = true;

//----------------- annotations ----------------
  public boolean USE_EXTERNAL_ANNOTATIONS = false;
  public boolean INSERT_OVERRIDE_ANNOTATION = true;

//----------------- override -------------------
  public boolean REPEAT_SYNCHRONIZED = true;

//----------------- IMPORTS --------------------

  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
  public boolean USE_FQ_CLASS_NAMES = false;
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;
  public boolean USE_SINGLE_CLASS_IMPORTS = true;
  public boolean INSERT_INNER_CLASS_IMPORTS = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();

//----------------- ORDER OF MEMBERS ------------------

  public int STATIC_FIELDS_ORDER_WEIGHT = 1;
  public int FIELDS_ORDER_WEIGHT = 2;
  public int CONSTRUCTORS_ORDER_WEIGHT = 3;
  public int STATIC_METHODS_ORDER_WEIGHT = 4;
  public int METHODS_ORDER_WEIGHT = 5;
  public int STATIC_INNER_CLASSES_ORDER_WEIGHT = 6;
  public int INNER_CLASSES_ORDER_WEIGHT = 7;

//----------------- WRAPPING ---------------------------
  public int RIGHT_MARGIN = 120;
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = false;


  // ---------------------------------- Javadoc formatting options -------------------------
  public boolean ENABLE_JAVADOC_FORMATTING = true;

  /**
   * Align parameter comments to longest parameter name
   */
  public boolean JD_ALIGN_PARAM_COMMENTS = true;

  /**
   * Align exception comments to longest exception name
   */
  public boolean JD_ALIGN_EXCEPTION_COMMENTS = true;

  public boolean JD_ADD_BLANK_AFTER_PARM_COMMENTS = false;
  public boolean JD_ADD_BLANK_AFTER_RETURN = false;
  public boolean JD_ADD_BLANK_AFTER_DESCRIPTION = true;
  public boolean JD_P_AT_EMPTY_LINES = true;

  public boolean JD_KEEP_INVALID_TAGS = true;
  public boolean JD_KEEP_EMPTY_LINES = true;
  public boolean JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false;

  public boolean JD_USE_THROWS_NOT_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_PARAMETER = true;
  public boolean JD_KEEP_EMPTY_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_RETURN = true;


  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;
  
  public boolean JD_PRESERVE_LINE_FEEDS = false;
  public boolean JD_PARAM_DESCRIPTION_ON_NEW_LINE = false;

  // ---------------------------------------------------------------------------------------


  // ---------------------------------- Legacy(!) XML formatting options -------------------

  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_KEEP_WHITESPACES = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public int XML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public int XML_TEXT_WRAP = WRAP_AS_NEEDED;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_KEEP_LINE_BREAKS = true;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_KEEP_LINE_BREAKS_IN_TEXT = true;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public int XML_KEEP_BLANK_LINES = 2;

  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_ALIGN_ATTRIBUTES = true;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_ALIGN_TEXT = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_SPACE_AFTER_TAG_NAME = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_SPACE_INSIDE_EMPTY_TAG = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public boolean XML_KEEP_WHITE_SPACES_INSIDE_CDATA = false;
  /**
   * @deprecated Use XmlCodeStyleSettings.
   */
  public int XML_WHITE_SPACE_AROUND_CDATA = 0;

  // ---------------------------------------------------------------------------------------

  // ---------------------------------- HTML formatting options -------------------------
  public boolean HTML_KEEP_WHITESPACES = false;
  public int HTML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  public int HTML_TEXT_WRAP = WRAP_AS_NEEDED;

  public boolean HTML_KEEP_LINE_BREAKS = true;
  public boolean HTML_KEEP_LINE_BREAKS_IN_TEXT = true;
  public int HTML_KEEP_BLANK_LINES = 2;

  public boolean HTML_ALIGN_ATTRIBUTES = true;
  public boolean HTML_ALIGN_TEXT = false;

  public boolean HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = false;
  public boolean HTML_SPACE_AFTER_TAG_NAME = false;
  public boolean HTML_SPACE_INSIDE_EMPTY_TAG = false;

  @NonNls public String HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = "body,div,p,form,h1,h2,h3";
  @NonNls public String HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = "br";
  @NonNls public String HTML_DO_NOT_INDENT_CHILDREN_OF = "html,body,thead,tbody,tfoot";
  public int HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = 200;

  @NonNls public String HTML_KEEP_WHITESPACES_INSIDE = "span,pre";
  @NonNls public String HTML_INLINE_ELEMENTS =
    "a,abbr,acronym,b,basefont,bdo,big,br,cite,cite,code,dfn,em,font,i,img,input,kbd,label,q,s,samp,select,span,strike,strong,sub,sup,textarea,tt,u,var";
  @NonNls public String HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = "title,h1,h2,h3,h4,h5,h6,p";

  // ---------------------------------------------------------------------------------------


  // true if <%page import="x.y.z, x.y.t"%>
  // false if <%page import="x.y.z"%>
  //          <%page import="x.y.t"%>
  public boolean JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST = false;

  //----------------------------------------------------------------------------------------

  // region Formatter control

  public boolean FORMATTER_TAGS_ENABLED = false;
  public String FORMATTER_ON_TAG = "@formatter:on";
  public String FORMATTER_OFF_TAG = "@formatter:off";

  public volatile boolean FORMATTER_TAGS_ACCEPT_REGEXP = false;
  private volatile Pattern myFormatterOffPattern = null;
  private volatile Pattern myFormatterOnPattern = null;

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

  @Override
  public void readExternal(Element element) throws InvalidDataException {
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
    boolean oldOptionsImported = importOldIndentOptions(element);
    for (final CustomCodeStyleSettings settings : myCustomSettings.values()) {
      settings.readExternal(element);
      settings.importLegacySettings();
    }

    final List list = element.getChildren(ADDITIONAL_INDENT_OPTIONS);
    if (list != null) {
      for(Object o:list) {
        if (o instanceof Element) {
          final Element additionalIndentElement = (Element)o;
          final String fileTypeId = additionalIndentElement.getAttributeValue(FILETYPE);

          if (fileTypeId != null && !fileTypeId.isEmpty()) {
            FileType target = FileTypeManager.getInstance().getFileTypeByExtension(fileTypeId);
            if (FileTypes.UNKNOWN == target || FileTypes.PLAIN_TEXT == target || target.getDefaultExtension().isEmpty()) {
              target = new TempFileType(fileTypeId);
            }

            final IndentOptions options = getDefaultIndentOptions(target);
            options.readExternal(additionalIndentElement);
            registerAdditionalIndentOptions(target, options);
          }
        }
      }
    }

    myCommonSettingsManager.readExternal(element);

    if (oldOptionsImported) {
      copyOldIndentOptions("java", JAVA_INDENT_OPTIONS);
      copyOldIndentOptions("jsp", JSP_INDENT_OPTIONS);
      copyOldIndentOptions("xml", XML_INDENT_OPTIONS);
    }

    if (USE_SAME_INDENTS) IGNORE_SAME_INDENTS_FOR_LANGUAGES = true;
  }

  private void copyOldIndentOptions(@NonNls final String extension, final IndentOptions options) {
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
    if (fileType != FileTypes.UNKNOWN && fileType != FileTypes.PLAIN_TEXT && !myAdditionalIndentOptions.containsKey(fileType) &&
        !fileType.getDefaultExtension().isEmpty()) {
      registerAdditionalIndentOptions(fileType, options);
      //
      // Upgrade to version 11
      //
      if (fileType instanceof LanguageFileType) {
        Language lang = ((LanguageFileType)fileType).getLanguage();
        CommonCodeStyleSettings langSettings = myCommonSettingsManager.getCommonSettings(lang);
        if (langSettings != this && langSettings.getIndentOptions() != null) {
          langSettings.importOldIndentOptions(this);
        }
      }
    }
  }

  private boolean importOldIndentOptions(@NonNls Element element) {
    final List options = element.getChildren("option");
    boolean optionsImported = false;
    for (Object option1 : options) {
      @NonNls Element option = (Element)option1;
      @NonNls final String name = option.getAttributeValue("name");
      if ("TAB_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.TAB_SIZE = value;
        JSP_INDENT_OPTIONS.TAB_SIZE = value;
        XML_INDENT_OPTIONS.TAB_SIZE = value;
        OTHER_INDENT_OPTIONS.TAB_SIZE = value;
        optionsImported = true;
      }
      else if ("INDENT_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.INDENT_SIZE = value;
        JSP_INDENT_OPTIONS.INDENT_SIZE = value;
        XML_INDENT_OPTIONS.INDENT_SIZE = value;
        OTHER_INDENT_OPTIONS.INDENT_SIZE = value;
        optionsImported = true;
      }
      else if ("CONTINUATION_INDENT_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        JSP_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        XML_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        OTHER_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        optionsImported = true;
      }
      else if ("USE_TAB_CHARACTER".equals(name)) {
        final boolean value = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        JAVA_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        JSP_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        XML_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        OTHER_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        optionsImported = true;
      }
      else if ("SMART_TABS".equals(name)) {
        final boolean value = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        JAVA_INDENT_OPTIONS.SMART_TABS = value;
        JSP_INDENT_OPTIONS.SMART_TABS = value;
        XML_INDENT_OPTIONS.SMART_TABS = value;
        OTHER_INDENT_OPTIONS.SMART_TABS = value;
        optionsImported = true;
      } else if ("SPACE_AFTER_UNARY_OPERATOR".equals(name)) {
        SPACE_AROUND_UNARY_OPERATOR = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        optionsImported = true;
      }
    }
    return optionsImported;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    final CodeStyleSettings parentSettings = new CodeStyleSettings();
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<CodeStyleSettings>(this, parentSettings));
    List<CustomCodeStyleSettings> customSettings = new ArrayList<CustomCodeStyleSettings>(myCustomSettings.values());
    
    Collections.sort(customSettings, new Comparator<CustomCodeStyleSettings>(){
      @Override
      public int compare(final CustomCodeStyleSettings o1, final CustomCodeStyleSettings o2) {
        return o1.getTagName().compareTo(o2.getTagName());
      }
    });

    for (final CustomCodeStyleSettings settings : customSettings) {
      final CustomCodeStyleSettings parentCustomSettings = parentSettings.getCustomSettings(settings.getClass());
      assert parentCustomSettings != null;
      settings.writeExternal(element, parentCustomSettings);
    }

    final FileType[] fileTypes = myAdditionalIndentOptions.keySet().toArray(new FileType[myAdditionalIndentOptions.keySet().size()]);
    Arrays.sort(fileTypes, new Comparator<FileType>() {
      @Override
      public int compare(final FileType o1, final FileType o2) {
        return o1.getDefaultExtension().compareTo(o2.getDefaultExtension());
      }
    });

    for (FileType fileType : fileTypes) {
      final IndentOptions indentOptions = myAdditionalIndentOptions.get(fileType);
      Element additionalIndentOptions = new Element(ADDITIONAL_INDENT_OPTIONS);
      indentOptions.serialize(additionalIndentOptions, getDefaultIndentOptions(fileType)) ;
      additionalIndentOptions.setAttribute(FILETYPE,fileType.getDefaultExtension());
      if (!additionalIndentOptions.getChildren().isEmpty()) {
        element.addContent(additionalIndentOptions);
      }
    }
    
    myCommonSettingsManager.writeExternal(element);
  }


  private static IndentOptions getDefaultIndentOptions(FileType fileType) {
    final FileTypeIndentOptionsProvider[] providers = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    for (final FileTypeIndentOptionsProvider provider : providers) {
      if (provider.getFileType().equals(fileType)) {
        return getFileTypeIndentOptions(provider);
      }
    }
    return new IndentOptions();
  }

  @Override
  @Nullable
  public IndentOptions getIndentOptions() {
    return OTHER_INDENT_OPTIONS;
  }

  /**
   * If the file type has an associated language and language indent options are defined, returns these options. Otherwise attempts to find
   * indent options from <code>FileTypeIndentOptionsProvider</code>. If none are found, other indent options are returned.
   * @param fileType The file type to search indent options for.
   * @return File type indent options or <code>OTHER_INDENT_OPTIONS</code>.
   *
   * @see FileTypeIndentOptionsProvider
   * @see LanguageCodeStyleSettingsProvider
   */
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
  
  @Nullable
  private IndentOptions getLanguageIndentOptions(@Nullable FileType fileType) {
    if (fileType == null || !(fileType instanceof LanguageFileType)) return null;
    Language lang = ((LanguageFileType)fileType).getLanguage();
    CommonCodeStyleSettings langSettings = getCommonSettings(lang);
    return langSettings == this ? null : langSettings.getIndentOptions();
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

  public int getLabelIndentSize(FileType fileType) {
    return getIndentOptions(fileType).LABEL_INDENT_SIZE;
  }

  public boolean getLabelIndentAbsolute(FileType fileType) {
    return getIndentOptions(fileType).LABEL_INDENT_ABSOLUTE;
  }

  public int getTabSize(FileType fileType) {
    return getIndentOptions(fileType).TAB_SIZE;
  }

  public boolean useTabCharacter(FileType fileType) {
    return getIndentOptions(fileType).USE_TAB_CHARACTER;
  }

  public static class TypeToNameMap implements JDOMExternalizable {
    private final List<String> myPatterns = new ArrayList<String>();
    private final List<String> myNames = new ArrayList<String>();

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

    public boolean equals(Object other) {
      if (other instanceof TypeToNameMap) {
        TypeToNameMap otherMap = (TypeToNameMap)other;
        return myPatterns.equals(otherMap.myPatterns) && myNames.equals(otherMap.myNames);
      }
      return false;
    }

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
  
  public void unregisterAdditionalIndentOptions(FileType fileType) {
    myAdditionalIndentOptions.remove(fileType);
  }

  public IndentOptions getAdditionalIndentOptions(FileType fileType) {
    if (!myLoadedAdditionalIndentOptions) {
      loadAdditionalIndentOptions();
    }
    return myAdditionalIndentOptions.get(fileType);
  }

  private void loadAdditionalIndentOptions() {
    synchronized (myAdditionalIndentOptions) {
      myLoadedAdditionalIndentOptions = true;
      final FileTypeIndentOptionsProvider[] providers = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
      for (final FileTypeIndentOptionsProvider provider : providers) {
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
    public String getCharset(@NotNull VirtualFile file, byte[] content) {
      return null;
    }
  }

  public CommonCodeStyleSettings getCommonSettings(Language lang) {
    return myCommonSettingsManager.getCommonSettings(lang);
  }

  /**
   * @param langName The language name. 
   * @return Language-specific code style settings or shared settings if not found.
   * @see CommonCodeStyleSettingsManager#getCommonSettings 
   */
  public CommonCodeStyleSettings getCommonSettings(String langName) {
    return myCommonSettingsManager.getCommonSettings(langName);
  }

}
