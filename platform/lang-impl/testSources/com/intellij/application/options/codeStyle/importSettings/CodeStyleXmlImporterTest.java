package com.intellij.application.options.codeStyle.importSettings;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeXmlImporter;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CodeStyleXmlImporterTest extends LightPlatformTestCase {
  public void testStandardCodeStyleXml() throws SchemeImportException {
    CodeStyleSettings settings= importSettings();
    assertEquals(false, settings.AUTODETECT_INDENTS);
    assertEquals(60, settings.getDefaultRightMargin());
  }
  
  public void testProjectCodeStyleSettings() throws SchemeImportException {
    CodeStyleSettings settings= importSettings();
    assertEquals(40, settings.getDefaultRightMargin());
    assertEquals(true, settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);
    assertEquals(true, settings.FORMATTER_TAGS_ENABLED);
    assertEquals(true, settings.FORMATTER_TAGS_ACCEPT_REGEXP);
  }
  
  private CodeStyleSettings importSettings() throws SchemeImportException {
    final CodeStyleScheme targetScheme = new CodeStyleSchemeImpl("Test", false, null);
    SchemeFactory<CodeStyleScheme> schemeFactory = new SchemeFactory<CodeStyleScheme>() {
      @Override
      public CodeStyleScheme createNewScheme(@Nullable String name) {
        return targetScheme;
      }
    };
    File ioFile = new File(getTestDataPath() + getTestName(true) + ".xml");
    assertExists(ioFile);
    VirtualFile vFile = VfsUtil.findFileByIoFile(ioFile, true);
    CodeStyleSchemeXmlImporter importer = new CodeStyleSchemeXmlImporter();
    return importer.importScheme(getProject(), vFile, targetScheme, schemeFactory).getCodeStyleSettings();
  }
  
  @NotNull
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/../../../platform/lang-impl/testData/codeStyle/importSettings/";
  }
}


