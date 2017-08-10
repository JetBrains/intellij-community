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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaCodeStyleSettings extends CustomCodeStyleSettings implements ImportsLayoutSettings {
  @NonNls private static final String REPEAT_ANNOTATIONS = "REPEAT_ANNOTATIONS";

  public JavaCodeStyleSettings(CodeStyleSettings container) {
    super("JavaCodeStyleSettings", container);
    initTypeToName();
    initImportsByDefault();
  }
  public String FIELD_NAME_PREFIX = "";
  public String STATIC_FIELD_NAME_PREFIX = "";
  public String PARAMETER_NAME_PREFIX = "";
  public String LOCAL_VARIABLE_NAME_PREFIX = "";

  public String FIELD_NAME_SUFFIX = "";
  public String STATIC_FIELD_NAME_SUFFIX = "";
  public String PARAMETER_NAME_SUFFIX = "";
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";

  public boolean PREFER_LONGER_NAMES = true;

  public boolean GENERATE_FINAL_LOCALS;
  public boolean GENERATE_FINAL_PARAMETERS;

  public String VISIBILITY = "public";

  public final CodeStyleSettings.TypeToNameMap FIELD_TYPE_TO_NAME = new CodeStyleSettings.TypeToNameMap();
  public final CodeStyleSettings.TypeToNameMap STATIC_FIELD_TYPE_TO_NAME = new CodeStyleSettings.TypeToNameMap();
  @NonNls public final CodeStyleSettings.TypeToNameMap PARAMETER_TYPE_TO_NAME = new CodeStyleSettings.TypeToNameMap();
  public final CodeStyleSettings.TypeToNameMap LOCAL_VARIABLE_TYPE_TO_NAME = new CodeStyleSettings.TypeToNameMap();

  public boolean USE_EXTERNAL_ANNOTATIONS;
  public boolean INSERT_OVERRIDE_ANNOTATION = true;

  public boolean REPEAT_SYNCHRONIZED = true;

  private List<String> myRepeatAnnotations = new ArrayList<>();

  public List<String> getRepeatAnnotations() {
    return myRepeatAnnotations;
  }

  public void setRepeatAnnotations(List<String> repeatAnnotations) {
    myRepeatAnnotations.clear();
    myRepeatAnnotations.addAll(repeatAnnotations);
  }

  public boolean REPLACE_INSTANCEOF = false;
  public boolean REPLACE_CAST = false;
  public boolean REPLACE_NULL_CHECK = true;

  public boolean SPACES_WITHIN_ANGLE_BRACKETS;

  //Type arguments
  public boolean SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT;

  //Type parameters
  public boolean SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER;
  public boolean SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true;

  public boolean DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION;

  public int ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
  public boolean ALIGN_MULTILINE_ANNOTATION_PARAMETERS;

  public int BLANK_LINES_AROUND_INITIALIZER = 1;
  
  public static final int FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED = 1;
  public static final int FULLY_QUALIFY_NAMES_ALWAYS = 2;
  public static final int SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT = 3;

  public int CLASS_NAMES_IN_JAVADOC = FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
  
  public boolean useFqNamesInJavadocAlways() {
    return CLASS_NAMES_IN_JAVADOC == FULLY_QUALIFY_NAMES_ALWAYS;
  }

  // Imports
  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
  public boolean USE_FQ_CLASS_NAMES;
  public boolean USE_SINGLE_CLASS_IMPORTS = true;
  public boolean INSERT_INNER_CLASS_IMPORTS;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();

  // region JavaDoc
  public boolean ENABLE_JAVADOC_FORMATTING = true;
  public boolean JD_ALIGN_PARAM_COMMENTS = true;
  public boolean JD_ALIGN_EXCEPTION_COMMENTS = true;
  public boolean JD_ADD_BLANK_AFTER_PARM_COMMENTS;
  public boolean JD_ADD_BLANK_AFTER_RETURN;
  public boolean JD_ADD_BLANK_AFTER_DESCRIPTION = true;
  public boolean JD_P_AT_EMPTY_LINES = true;

  public boolean JD_KEEP_INVALID_TAGS = true;
  public boolean JD_KEEP_EMPTY_LINES = true;
  public boolean JD_DO_NOT_WRAP_ONE_LINE_COMMENTS;

  public boolean JD_USE_THROWS_NOT_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_PARAMETER = true;
  public boolean JD_KEEP_EMPTY_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_RETURN = true;


  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;
  public boolean JD_PRESERVE_LINE_FEEDS;
  public boolean JD_PARAM_DESCRIPTION_ON_NEW_LINE;

  public boolean JD_INDENT_ON_CONTINUATION = false;
  
  // endregion
  
  @Override
  public boolean isLayoutStaticImportsSeparately() {
    return LAYOUT_STATIC_IMPORTS_SEPARATELY;
  }

  @Override
  public void setLayoutStaticImportsSeparately(boolean value) {
    LAYOUT_STATIC_IMPORTS_SEPARATELY = value;

  }

  @Override
  public int getNamesCountToUseImportOnDemand() {
    return NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public void setNamesCountToUseImportOnDemand(int value) {
    NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Override
  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Override
  public boolean isInsertInnerClassImports() {
    return INSERT_INNER_CLASS_IMPORTS;
  }

  @Override
  public void setInsertInnerClassImports(boolean value) {
    INSERT_INNER_CLASS_IMPORTS = value;
  }

  @Override
  public boolean isUseSingleClassImports() {
    return USE_SINGLE_CLASS_IMPORTS;
  }

  @Override
  public void setUseSingleClassImports(boolean value) {
    USE_SINGLE_CLASS_IMPORTS = value;
  }

  @Override
  public boolean isUseFqClassNames() {
    return USE_FQ_CLASS_NAMES;
  }

  @Override
  public void setUseFqClassNames(boolean value) {
    USE_FQ_CLASS_NAMES = value;
  }

  @Override
  public PackageEntryTable getImportLayoutTable() {
    return IMPORT_LAYOUT_TABLE;
  }

  @Override
  public PackageEntryTable getPackagesToUseImportOnDemand() {
    return PACKAGES_TO_USE_IMPORT_ON_DEMAND;
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


  @SuppressWarnings("unused") // Used in objectEquals.vm
  public boolean isGenerateFinalLocals() {
    return GENERATE_FINAL_LOCALS;
  }

  @SuppressWarnings("unused") // Used in objectEquals.vm
  public boolean isGenerateFinalParameters() {
    return GENERATE_FINAL_PARAMETERS;
  }

  @SuppressWarnings("Duplicates")
  private static void initGeneralLocalVariable(@NonNls CodeStyleSettings.TypeToNameMap map) {
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

  private void initTypeToName() {
    initGeneralLocalVariable(PARAMETER_TYPE_TO_NAME);
    initGeneralLocalVariable(LOCAL_VARIABLE_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.addPair("*Exception", "e");
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
    USE_EXTERNAL_ANNOTATIONS = rootSettings.USE_EXTERNAL_ANNOTATIONS;
    INSERT_OVERRIDE_ANNOTATION = rootSettings.INSERT_OVERRIDE_ANNOTATION;
    REPEAT_SYNCHRONIZED = rootSettings.REPEAT_SYNCHRONIZED;
    setRepeatAnnotations(rootSettings.getRepeatAnnotations());
    LAYOUT_STATIC_IMPORTS_SEPARATELY = rootSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY;
    USE_FQ_CLASS_NAMES = rootSettings.USE_FQ_CLASS_NAMES;
    USE_SINGLE_CLASS_IMPORTS = rootSettings.USE_SINGLE_CLASS_IMPORTS;
    INSERT_INNER_CLASS_IMPORTS = rootSettings.INSERT_INNER_CLASS_IMPORTS;
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = rootSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = rootSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(rootSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    IMPORT_LAYOUT_TABLE.copyFrom(rootSettings.IMPORT_LAYOUT_TABLE);
    REPLACE_INSTANCEOF = rootSettings.REPLACE_INSTANCEOF;
    REPLACE_CAST = rootSettings.REPLACE_CAST;
    REPLACE_NULL_CHECK = rootSettings.REPLACE_NULL_CHECK;
    FIELD_NAME_PREFIX = rootSettings.FIELD_NAME_PREFIX;
    STATIC_FIELD_NAME_PREFIX = rootSettings.STATIC_FIELD_NAME_PREFIX;
    PARAMETER_NAME_PREFIX = rootSettings.PARAMETER_NAME_PREFIX;
    LOCAL_VARIABLE_NAME_PREFIX = rootSettings.LOCAL_VARIABLE_NAME_PREFIX;

    FIELD_NAME_SUFFIX = rootSettings.FIELD_NAME_SUFFIX;
    STATIC_FIELD_NAME_SUFFIX = rootSettings.STATIC_FIELD_NAME_SUFFIX;
    PARAMETER_NAME_SUFFIX = rootSettings.PARAMETER_NAME_SUFFIX;
    LOCAL_VARIABLE_NAME_SUFFIX = rootSettings.LOCAL_VARIABLE_NAME_SUFFIX;

    PREFER_LONGER_NAMES = rootSettings.PREFER_LONGER_NAMES;
    GENERATE_FINAL_LOCALS = rootSettings.GENERATE_FINAL_LOCALS;
    GENERATE_FINAL_PARAMETERS = rootSettings.GENERATE_FINAL_PARAMETERS;
    VISIBILITY = rootSettings.VISIBILITY;
    FIELD_TYPE_TO_NAME.copyFrom(rootSettings.FIELD_TYPE_TO_NAME);
    LOCAL_VARIABLE_TYPE_TO_NAME.copyFrom(rootSettings.LOCAL_VARIABLE_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.copyFrom(rootSettings.PARAMETER_TYPE_TO_NAME);
    STATIC_FIELD_TYPE_TO_NAME.copyFrom(rootSettings.STATIC_FIELD_TYPE_TO_NAME);

    ENABLE_JAVADOC_FORMATTING = rootSettings.ENABLE_JAVADOC_FORMATTING;
    JD_ALIGN_PARAM_COMMENTS = rootSettings.JD_ALIGN_PARAM_COMMENTS;
    JD_ALIGN_EXCEPTION_COMMENTS = rootSettings.JD_ALIGN_EXCEPTION_COMMENTS;
    JD_ADD_BLANK_AFTER_PARM_COMMENTS = rootSettings.JD_ADD_BLANK_AFTER_PARM_COMMENTS;
    JD_ADD_BLANK_AFTER_RETURN = rootSettings.JD_ADD_BLANK_AFTER_RETURN;
    JD_ADD_BLANK_AFTER_DESCRIPTION = rootSettings.JD_ADD_BLANK_AFTER_DESCRIPTION;
    JD_P_AT_EMPTY_LINES = rootSettings.JD_P_AT_EMPTY_LINES;

    JD_KEEP_INVALID_TAGS = rootSettings.JD_KEEP_INVALID_TAGS;
    JD_KEEP_EMPTY_LINES = rootSettings.JD_KEEP_EMPTY_LINES;
    JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = rootSettings.JD_DO_NOT_WRAP_ONE_LINE_COMMENTS;

    JD_USE_THROWS_NOT_EXCEPTION = rootSettings.JD_USE_THROWS_NOT_EXCEPTION;
    JD_KEEP_EMPTY_PARAMETER = rootSettings.JD_KEEP_EMPTY_PARAMETER;
    JD_KEEP_EMPTY_EXCEPTION = rootSettings.JD_KEEP_EMPTY_EXCEPTION;
    JD_KEEP_EMPTY_RETURN = rootSettings.JD_KEEP_EMPTY_RETURN;


    JD_LEADING_ASTERISKS_ARE_ENABLED = rootSettings.JD_LEADING_ASTERISKS_ARE_ENABLED;
    JD_PRESERVE_LINE_FEEDS = rootSettings.JD_PRESERVE_LINE_FEEDS;
    JD_PARAM_DESCRIPTION_ON_NEW_LINE = rootSettings.JD_PARAM_DESCRIPTION_ON_NEW_LINE;

    JD_INDENT_ON_CONTINUATION = rootSettings.JD_INDENT_ON_CONTINUATION;
  }

  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    super.readExternal(parentElement);
    Element child = parentElement.getChild(getTagName());
    if (child != null) {
      myRepeatAnnotations.clear();
      Element annotations = child.getChild(REPEAT_ANNOTATIONS);
      if (annotations != null) {
        for (Element anno : annotations.getChildren("ANNO")) {
          myRepeatAnnotations.add(anno.getAttributeValue("name"));
        }
      }
    }
  }

  @Override
  public void writeExternal(Element parentElement, @NotNull CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    super.writeExternal(parentElement, parentSettings);
    if (!myRepeatAnnotations.isEmpty()) {
      Element child = parentElement.getChild(getTagName());
      if (child == null) {
        child = new Element(getTagName());
      }
      Element annos = new Element(REPEAT_ANNOTATIONS);
      for (String annotation : myRepeatAnnotations) {
        annos.addContent(new Element("ANNO").setAttribute("name", annotation));
      }
      child.addContent(annos);
    }
  }

  public static JavaCodeStyleSettings getInstance(@NotNull Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
  }
}
