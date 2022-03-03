// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.configurationStore.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.WrapConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class JavaCodeStyleSettings extends CustomCodeStyleSettings implements ImportsLayoutSettings {
  private static final String REPEAT_ANNOTATIONS = "REPEAT_ANNOTATIONS";
  private static final String REPEAT_ANNOTATIONS_ITEM = "ANNO";
  private static final String DO_NOT_IMPORT_INNER = "DO_NOT_IMPORT_INNER";
  private static final String DO_NOT_IMPORT_INNER_ITEM = "CLASS";
  private static final String COLLECTION_ITEM_ATTRIBUTE = "name";

  public JavaCodeStyleSettings(CodeStyleSettings container) {
    super("JavaCodeStyleSettings", container);
    initImportsByDefault();
  }
  public String FIELD_NAME_PREFIX = "";
  public String STATIC_FIELD_NAME_PREFIX = "";
  public String PARAMETER_NAME_PREFIX = "";
  public String LOCAL_VARIABLE_NAME_PREFIX = "";
  public String TEST_NAME_PREFIX = "";
  public String SUBCLASS_NAME_PREFIX = "";

  public String FIELD_NAME_SUFFIX = "";
  public String STATIC_FIELD_NAME_SUFFIX = "";
  public String PARAMETER_NAME_SUFFIX = "";
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";
  public String TEST_NAME_SUFFIX = "Test";
  public String SUBCLASS_NAME_SUFFIX = "Impl";

  public boolean PREFER_LONGER_NAMES = true;

  public boolean GENERATE_FINAL_LOCALS;
  public boolean GENERATE_FINAL_PARAMETERS;

  @PsiModifier.ModifierConstant
  public String VISIBILITY = "public";

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

  private List<String> myDoNotImportInner = new ArrayList<>();

  public List<String> getDoNotImportInner() {
    return myDoNotImportInner;
  }

  public void setDoNotImportInner(List<String> doNotImportInner) {
    myDoNotImportInner = doNotImportInner;
  }

  /** @deprecated Use {@link #REPLACE_INSTANCEOF_AND_CAST} */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean REPLACE_INSTANCEOF = false;
  /** @deprecated Use {@link #REPLACE_INSTANCEOF_AND_CAST} */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean REPLACE_CAST = false;
  public boolean REPLACE_INSTANCEOF_AND_CAST = false;
  public boolean REPLACE_NULL_CHECK = true;

  @Property(externalName = "replace_sum_lambda_with_method_ref")
  public boolean REPLACE_SUM = true;

  public boolean SPACES_WITHIN_ANGLE_BRACKETS;

  //Type arguments
  public boolean SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT;

  //Type parameters
  public boolean SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER;
  public boolean SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true;

  // Only related to fields!
  // @Foo int field;
  public boolean DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION;

  // @Foo int param
  public boolean DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER = false;

  @WrapConstant
  public int ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
  public boolean ALIGN_MULTILINE_ANNOTATION_PARAMETERS;
  public boolean NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = false;
  public boolean RPAREN_ON_NEW_LINE_IN_ANNOTATION = false;

  public boolean SPACE_AROUND_ANNOTATION_EQ = true;

  public boolean ALIGN_MULTILINE_TEXT_BLOCKS = false;

  public int BLANK_LINES_AROUND_INITIALIZER = 1;

  public static final int FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED = 1;
  public static final int FULLY_QUALIFY_NAMES_ALWAYS = 2;
  public static final int SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT = 3;

  public int CLASS_NAMES_IN_JAVADOC = FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
  public boolean SPACE_BEFORE_COLON_IN_FOREACH = true;
  public boolean SPACE_INSIDE_ONE_LINE_ENUM_BRACES = false;

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
  public PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  @Property(externalName = "imports_layout")
  public PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();


  @WrapConstant
  public int RECORD_COMPONENTS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  public boolean ALIGN_MULTILINE_RECORDS = true;
  public boolean NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false;
  public boolean RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = false;
  /**
   * "record R( String s )"
   * or
   * "record R(String s)"
   */
  public boolean SPACE_WITHIN_RECORD_HEADER = false;

  @WrapConstant
  public int MULTI_CATCH_TYPES_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  public boolean ALIGN_TYPES_IN_MULTI_CATCH = true;


  // region JavaDoc
  @Property(externalName = "doc_enable_formatting")
  public boolean ENABLE_JAVADOC_FORMATTING = true;
  @Property(externalName = "doc_align_param_comments")
  public boolean JD_ALIGN_PARAM_COMMENTS = true;
  @Property(externalName = "doc_align_exception_comments")
  public boolean JD_ALIGN_EXCEPTION_COMMENTS = true;
  @Property(externalName = "doc_add_blank_line_after_param_comments")
  public boolean JD_ADD_BLANK_AFTER_PARM_COMMENTS;
  @Property(externalName = "doc_add_blank_line_after_return")
  public boolean JD_ADD_BLANK_AFTER_RETURN;
  @Property(externalName = "doc_add_blank_line_after_description")
  public boolean JD_ADD_BLANK_AFTER_DESCRIPTION = true;
  @Property(externalName = "doc_add_p_tag_on_empty_lines")
  public boolean JD_P_AT_EMPTY_LINES = true;

  @Property(externalName = "doc_keep_invalid_tags")
  public boolean JD_KEEP_INVALID_TAGS = true;
  @Property(externalName = "doc_keep_empty_lines")
  public boolean JD_KEEP_EMPTY_LINES = true;
  @Property(externalName = "doc_do_not_wrap_if_one_line")
  public boolean JD_DO_NOT_WRAP_ONE_LINE_COMMENTS;

  @Property(externalName = "doc_use_throws_not_exception_tag")
  public boolean JD_USE_THROWS_NOT_EXCEPTION = true;
  @Property(externalName = "doc_keep_empty_parameter_tag")
  public boolean JD_KEEP_EMPTY_PARAMETER = true;
  @Property(externalName = "doc_keep_empty_throws_tag")
  public boolean JD_KEEP_EMPTY_EXCEPTION = true;
  @Property(externalName = "doc_keep_empty_return_tag")
  public boolean JD_KEEP_EMPTY_RETURN = true;

  @Property(externalName = "doc_enable_leading_asterisks")
  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;
  @Property(externalName = "doc_preserve_line_breaks")
  public boolean JD_PRESERVE_LINE_FEEDS;
  @Property(externalName = "doc_param_description_on_new_line")
  public boolean JD_PARAM_DESCRIPTION_ON_NEW_LINE;

  @Property(externalName = "doc_indent_on_continuation")
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

  public boolean isInsertInnerClassImportsFor(String className) {
    return INSERT_INNER_CLASS_IMPORTS && !myDoNotImportInner.contains(className);
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

    ENABLE_JAVADOC_FORMATTING = rootSettings.ENABLE_JAVADOC_FORMATTING;

    JD_LEADING_ASTERISKS_ARE_ENABLED = rootSettings.JD_LEADING_ASTERISKS_ARE_ENABLED;
  }

  @Override
  public Object clone() {
    JavaCodeStyleSettings cloned = (JavaCodeStyleSettings)super.clone();
    cloned.myRepeatAnnotations = new ArrayList<>();
    cloned.setRepeatAnnotations(getRepeatAnnotations());
    cloned.PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
    cloned.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    cloned.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
    cloned.IMPORT_LAYOUT_TABLE.copyFrom(IMPORT_LAYOUT_TABLE);
    return cloned;
  }

  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    super.readExternal(parentElement);
    readExternalCollection(parentElement, myRepeatAnnotations, REPEAT_ANNOTATIONS, REPEAT_ANNOTATIONS_ITEM);
    readExternalCollection(parentElement, myDoNotImportInner, DO_NOT_IMPORT_INNER, DO_NOT_IMPORT_INNER_ITEM);
  }

  @Override
  public void writeExternal(Element parentElement, @NotNull CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    super.writeExternal(parentElement, parentSettings);
    writeExternalCollection(parentElement, myRepeatAnnotations, REPEAT_ANNOTATIONS, REPEAT_ANNOTATIONS_ITEM);
    writeExternalCollection(parentElement, myDoNotImportInner, DO_NOT_IMPORT_INNER, DO_NOT_IMPORT_INNER_ITEM);
  }

  public static JavaCodeStyleSettings getInstance(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class);
  }

  /**
   * For production code use {@link #getInstance(PsiFile)}
   */
  @TestOnly
  public static JavaCodeStyleSettings getInstance(@NotNull Project project) {
    return CodeStyle.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
  }

  private void readExternalCollection(Element parentElement, Collection<? super String> collection, String collectionName, String itemName) {
    Element child = parentElement.getChild(getTagName());
    if (child != null) {
      collection.clear();
      Element item = child.getChild(collectionName);
      if (item != null) {
        for (Element element : item.getChildren(itemName)) {
          collection.add(element.getAttributeValue(COLLECTION_ITEM_ATTRIBUTE));
        }
      }
    }
  }

  private void writeExternalCollection(Element parentElement,
                                       Collection<String> collection,
                                       String collectionName,
                                       String itemName) {
    if (!collection.isEmpty()) {
      Element child = parentElement.getChild(getTagName());
      if (child == null) {
        child = new Element(getTagName());
        parentElement.addContent(child);
      }
      Element element = new Element(collectionName);
      for (String item : collection) {
        element.addContent(new Element(itemName).setAttribute(COLLECTION_ITEM_ATTRIBUTE, item));
      }
      child.addContent(element);
    }
  }

  @Override
  protected void afterLoaded() {
    REPLACE_INSTANCEOF_AND_CAST |= REPLACE_CAST || REPLACE_INSTANCEOF;
    REPLACE_CAST = REPLACE_INSTANCEOF = false;
  }

  @NotNull
  @Override
  public List<String> getKnownTagNames() {
    return Arrays.asList(getTagName(), REPEAT_ANNOTATIONS, DO_NOT_IMPORT_INNER);
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj)) return false;
    JavaCodeStyleSettings otherSettings = (JavaCodeStyleSettings)obj;
    if (!myRepeatAnnotations.equals(otherSettings.getRepeatAnnotations())) return false;
    return myDoNotImportInner.equals(otherSettings.getDoNotImportInner());
  }
}
