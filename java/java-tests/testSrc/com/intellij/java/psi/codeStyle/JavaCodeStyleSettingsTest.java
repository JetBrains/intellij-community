// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.ide.codeStyleSettings.CodeStyleTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.codeStyle.json.CodeStyleSchemeJsonExporter;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("SameParameterValue")
public class JavaCodeStyleSettingsTest extends CodeStyleTestCase {

  public void testSettingsClone() {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    original.getImportLayoutTable().addEntry(new PackageEntry(false, "test", true));
    List<String> annotations = Arrays.asList("anno1", "anno2");
    original.setRepeatAnnotations(annotations);
    original.getPackagesToUseImportOnDemand().addEntry(new PackageEntry(false, "test2", true));

    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    assertEquals(annotations, copy.getRepeatAnnotations());
    assertEquals("Import tables do not match", original.getImportLayoutTable(), copy.getImportLayoutTable());
    assertEquals("On demand packages do not match", original.getPackagesToUseImportOnDemand(), copy.getPackagesToUseImportOnDemand());

    copy.setRepeatAnnotations(Collections.singletonList("anno1"));
    assertNotSame("Changed repeated annotations should reflect the equality relation", original, copy);
  }

  public void testSettingsCloneNotReferencingOriginal() throws IllegalAccessException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings original = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    CodeStyleSettings clonedRoot = CodeStyle.createTestSettings(originalRoot);
    JavaCodeStyleSettings copy = clonedRoot.getCustomSettings(JavaCodeStyleSettings.class);
    assertSame(clonedRoot, copy.getContainer());
    for (Field field : copy.getClass().getDeclaredFields()) {
      if (!isPrimitiveOrString(field.getType()) && (field.getModifiers() & Modifier.PUBLIC) != 0) {
        assertNotSame("Fields '" + field.getName() + "' reference the same value", field.get(original), field.get(copy));
      }
    }
  }

  public void testImportPre173Settings() throws SchemeImportException {
    CodeStyleSettings imported = importSettings();
    assertEquals("testprefix", imported.getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX);
  }

  public void testJsonExport() throws IOException {
    CodeStyleScheme testScheme = createTestScheme();
    final CodeStyleSettings settings = testScheme.getCodeStyleSettings();
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    settings.setSoftMargins(JavaLanguage.INSTANCE, Arrays.asList(11, 22));
    commonJavaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    commonJavaSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    commonJavaSettings.WRAP_ON_TYPING = CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue;
    commonJavaSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.FIELD_NAME_PREFIX = "m_";
    javaSettings.STATIC_FIELD_NAME_SUFFIX = "_s";
    javaSettings.setRepeatAnnotations(Arrays.asList("com.jetbrains.First", "com.jetbrains.Second"));

    CodeStyleSchemeJsonExporter exporter = new CodeStyleSchemeJsonExporter();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    exporter.exportScheme(testScheme, outputStream, Collections.singletonList("java"));
    compareWithExpected(outputStream.toString(), "json");
  }

  public void testNotFirstImportModule() throws IOException {
    CodeStyleScheme testScheme = new CodeStyleScheme() {

      @NotNull
      @Override
      public String getName() {
        return "Test";
      }

      @Override
      public boolean isDefault() {
        return false;
      }

      @NotNull
      @Override
      public CodeStyleSettings getCodeStyleSettings() {
        try {
          return importSettings();
        }
        catch (SchemeImportException e) {
          throw new RuntimeException(e);
        }
      }
    };
    CodeStyleSchemeJsonExporter exporter = new CodeStyleSchemeJsonExporter();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    exporter.exportScheme(testScheme, outputStream, Collections.singletonList("java"));
    compareWithExpected(outputStream.toString(), "json");
  }

  public void testFirstNotImportedImportModule() throws IOException, JDOMException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings settings = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    String text = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>
      """;
    settings.readExternal(JDOMUtil.load(text));

    Element root = new Element("root");
    PackageEntry moduleEntry = settings.IMPORT_LAYOUT_TABLE.getEntryAt(0);
    assertSame(PackageEntry.ALL_MODULE_IMPORTS, moduleEntry);
    settings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(true, "org.foo", true));
    settings.writeExternal(root, new JavaCodeStyleSettings(originalRoot));
    String actual = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
              <package name="org.foo" withSubpackages="true" static="true" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>""";
    assertEquals(actual, JDOMUtil.writeElement(root));
  }

  public void testMovedFirstNotImportedImportModule() throws IOException, JDOMException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings settings = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    String text = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>
      """;
    settings.readExternal(JDOMUtil.load(text));

    Element root = new Element("root");
    PackageEntry moduleEntry = settings.IMPORT_LAYOUT_TABLE.getEntryAt(0);
    assertSame(PackageEntry.ALL_MODULE_IMPORTS, moduleEntry);
    settings.IMPORT_LAYOUT_TABLE.removeEntryAt(0);
    settings.IMPORT_LAYOUT_TABLE.insertEntryAt(moduleEntry, 1);

    settings.writeExternal(root, new JavaCodeStyleSettings(originalRoot));
    String actual = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>""";
    assertEquals(actual, JDOMUtil.writeElement(root));
  }

  public void testMovedFirstImportedImportModule() throws IOException, JDOMException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings settings = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    String text = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>
      """;
    settings.readExternal(JDOMUtil.load(text));

    Element root = new Element("root");
    PackageEntry moduleEntry = settings.IMPORT_LAYOUT_TABLE.getEntryAt(0);
    assertSame(PackageEntry.ALL_MODULE_IMPORTS, moduleEntry);
    settings.IMPORT_LAYOUT_TABLE.removeEntryAt(0);
    settings.IMPORT_LAYOUT_TABLE.insertEntryAt(moduleEntry, 1);

    settings.writeExternal(root, new JavaCodeStyleSettings(originalRoot));
    String actual = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>""";
    assertEquals(actual, JDOMUtil.writeElement(root));
  }

  public void testMovedNotFirstImportedImportModule() throws IOException, JDOMException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings settings = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    String text = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>
      """;
    settings.readExternal(JDOMUtil.load(text));

    Element root = new Element("root");
    PackageEntry notModuleEntry = settings.IMPORT_LAYOUT_TABLE.getEntryAt(0);
    assertNotSame(PackageEntry.ALL_MODULE_IMPORTS, notModuleEntry);
    settings.IMPORT_LAYOUT_TABLE.removeEntryAt(0);
    settings.IMPORT_LAYOUT_TABLE.insertEntryAt(notModuleEntry, 1);

    settings.writeExternal(root, new JavaCodeStyleSettings(originalRoot));
    String actual = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="false" />
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>""";
    assertEquals(actual, JDOMUtil.writeElement(root));
  }

  public void testFirstImportedImportModule() throws IOException, JDOMException {
    CodeStyleSettings originalRoot = CodeStyle.getSettings(getProject());
    JavaCodeStyleSettings settings = originalRoot.getCustomSettings(JavaCodeStyleSettings.class);
    String text = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>
      """;
    settings.readExternal(JDOMUtil.load(text));

    Element root = new Element("root");
    PackageEntryTable table = settings.IMPORT_LAYOUT_TABLE;
    assertSize(4,table.getEntries());
    PackageEntry moduleEntry = table.getEntryAt(0);
    assertSame(PackageEntry.ALL_MODULE_IMPORTS, moduleEntry);
    table.addEntry(new PackageEntry(true, "org.foo", true));
    settings.writeExternal(root, new JavaCodeStyleSettings(originalRoot));
    String actual = """
      <root>
        <JavaCodeStyleSettings>
          <option name="IMPORT_LAYOUT_TABLE">
            <value>
              <package name="" withSubpackages="true" static="false" module="true" />
              <package name="" withSubpackages="true" static="true" />
              <package name="" withSubpackages="true" static="false" />
              <package name="com" withSubpackages="true" static="false" />
              <package name="org.foo" withSubpackages="true" static="true" />
            </value>
          </option>
        </JavaCodeStyleSettings>
      </root>""";
    assertEquals(actual, JDOMUtil.writeElement(root));
  }

  public void testSetProperties() {
    final CodeStyleSettings settings = getCurrentCodeStyleSettings();
    AbstractCodeStylePropertyMapper mapper =
      LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE).getPropertyMapper(settings);
    setSimple(mapper, "align_group_field_declarations", "true");
    setSimple(mapper, "blank_lines_after_class_header", "1");
    setSimple(mapper, "block_brace_style", "next_line");
    setSimple(mapper, "indent_size", "2");
    setSimple(mapper, "doc_align_param_comments", "true");
    setList(mapper, "imports_layout",
            Arrays.asList("com.jetbrains.*", "|", "org.eclipse.bar", "$**", "$org.eclipse.foo.**"));
    mapper.getAccessor("repeat_annotations").setFromString(" com.jetbrains.First,  com.jetbrains.Second");
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    assertTrue(commonJavaSettings.ALIGN_GROUP_FIELD_DECLARATIONS);
    assertEquals(1, commonJavaSettings.BLANK_LINES_AFTER_CLASS_HEADER);
    assertEquals(CommonCodeStyleSettings.NEXT_LINE, commonJavaSettings.BRACE_STYLE);
    assertEquals(2, commonJavaSettings.getIndentOptions().INDENT_SIZE);
    assertTrue(javaSettings.JD_ALIGN_PARAM_COMMENTS);
    PackageEntryTable importsTable = javaSettings.getImportLayoutTable();
    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, importsTable.getEntryAt(0));
    assertEquals(new PackageEntry(false, "com.jetbrains", false), importsTable.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, importsTable.getEntryAt(2));
    assertEquals(new PackageEntry(false, "org.eclipse.bar", false), importsTable.getEntryAt(3));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, importsTable.getEntryAt(4));
    assertEquals(new PackageEntry(true, "org.eclipse.foo", true), importsTable.getEntryAt(5));
    List<String> repeatAnno = javaSettings.getRepeatAnnotations();
    assertEquals(2, repeatAnno.size());
    assertEquals("com.jetbrains.First", repeatAnno.get(0));
    assertEquals("com.jetbrains.Second", repeatAnno.get(1));
  }

  private static void setSimple(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String name, @NotNull String value) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(name);
    assertNotNull(name + " not found", accessor);
    accessor.setFromString(value);
  }

  private static void setList(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String name, @NotNull List<String> value) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(name);
    assertNotNull(name + " not found", accessor);
    //noinspection unchecked
    accessor.set(value);
  }

  public void testFirstMigration() throws SchemeImportException {
    CodeStyleSettings initialSettings = createTestScheme().getCodeStyleSettings();

    CommonCodeStyleSettings initialCommonCodeStyleSettings = initialSettings.getCommonSettings(JavaLanguage.INSTANCE);
    JavaCodeStyleSettings initialCustomCodeStyleSettings = initialSettings.getCustomSettings(JavaCodeStyleSettings.class);

    assertEquals(0, initialCommonCodeStyleSettings.BLANK_LINES_AROUND_FIELD);
    assertEquals(0, initialCustomCodeStyleSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS);

    CodeStyleSettings settings = importSettings();

    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    assertEquals(7, commonSettings.BLANK_LINES_AROUND_FIELD);
    assertEquals(7, customSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS);
  }

  public void testWithoutModulesAndOtherImport() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(8, table.getEntries());

    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(3));
    assertEquals("javax", table.getEntryAt(4).getPackageName());
    assertEquals("java", table.getEntryAt(5).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(6));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(7));
  }

  public void testWithoutModulesAndOtherImportAndStaticImport() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(8, table.getEntries());

    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(2));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(3));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(4));
    assertEquals("javax", table.getEntryAt(5).getPackageName());
    assertEquals("java", table.getEntryAt(6).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(7));
  }

  public void testWithoutModules() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(8, table.getEntries());
    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(3));
    assertEquals("javax", table.getEntryAt(4).getPackageName());
    assertEquals("java", table.getEntryAt(5).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(6));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(7));
  }

  public void testOnlyModules() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(3, table.getEntries());
    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(2));
  }

  public void testWithoutOtherImportWithModule() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(8, table.getEntries());
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(0));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals("javax", table.getEntryAt(3).getPackageName());
    assertEquals("java", table.getEntryAt(4).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(5));
    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(6));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(7));
  }

  public void testWithoutStaticImportWithoutModule() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(8, table.getEntries());

    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(3));
    assertEquals("javax", table.getEntryAt(4).getPackageName());
    assertEquals("java", table.getEntryAt(5).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(6));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(7));
  }

  public void testWithoutStaticImportWithoutModuleAndStaticNotSeparate() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(7, table.getEntries());

    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals("javax", table.getEntryAt(3).getPackageName());
    assertEquals("java", table.getEntryAt(4).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(5));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(6));
  }

  public void testEmptyConfigImport() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();

    JavaCodeStyleSettings customSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    PackageEntryTable table = customSettings.IMPORT_LAYOUT_TABLE;
    assertSize(7, table.getEntries());
    assertEquals(PackageEntry.ALL_MODULE_IMPORTS, table.getEntryAt(0));
    assertEquals(PackageEntry.ALL_OTHER_IMPORTS_ENTRY, table.getEntryAt(1));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(2));
    assertEquals("javax", table.getEntryAt(3).getPackageName());
    assertEquals("java", table.getEntryAt(4).getPackageName());
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, table.getEntryAt(5));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, table.getEntryAt(6));
  }

  private static boolean isPrimitiveOrString(Class type) {
    return type.isPrimitive() || type.equals(String.class);
  }

  @Override
  protected String getBasePath() {
    return PathManagerEx.getTestDataPath() + "/codeStyle";
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "";
  }
}
