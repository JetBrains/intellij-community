// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.DynamicPluginsTestUtilKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class FileBasedIndexTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSurviveOnFileTypeChange() {
    myFixture.configureByText("Foo.java", "class Foo { String bar; }");
    myFixture.testHighlighting();
    FileType foo = FileTypeIndexTest.registerFakeFileType();
    FileTypeManagerEx.getInstanceEx().unregisterFileType(foo);
    myFixture.configureByText("Bar.java", "class Bar { String bar; }");
    myFixture.testHighlighting();
  }

  public void testLargeFile() {
    char[] text = new char[FileUtilRt.LARGE_FOR_CONTENT_LOADING + 42];
    final String clazz = "class Foo { String bar; }";
    for (int i = 0; i < text.length; i++) {
      text[i] = i < clazz.length() ? clazz.charAt(i) : ' ';
    }
    final LightVirtualFile file = new LightVirtualFile("Foo.java", new CharArrayCharSequence(text));
    assertFalse(((FileBasedIndexImpl)FileBasedIndex.getInstance()).isIndexingCandidate(file, StubUpdatingIndex.INDEX_ID));
  }

  public void testIndexVersionChanged() {
    myFixture.addFileToProject("file0.java", "");
    myFixture.addFileToProject("file1.java", "");
    String text = "<fileBasedIndex implementation=\"" + MyDummyFBIExtension.class.getName() + "\"/>";
    VERSION = 0;
    Disposable disposable = DynamicPluginsTestUtilKt.loadExtensionWithText(text, this.getClass().getClassLoader());
    Disposer.register(getProject(), disposable);
    assertEquals("file0.java", assertOneElement(FileBasedIndex.getInstance().getAllKeys(MyDummyFBIExtension.INDEX_ID, getProject())));

    FileBasedIndexSwitcher switcher = new FileBasedIndexSwitcher();
    try {
      switcher.turnOff();
      VERSION = 1;
    } finally {
      switcher.turnOn();
    }

    assertEquals("file1.java", assertOneElement(FileBasedIndex.getInstance().getAllKeys(MyDummyFBIExtension.INDEX_ID, getProject())));
  }

  private static volatile int VERSION = 0;

  public static class MyDummyFBIExtension extends FileBasedIndexExtension<String, String> {
    private static final @NotNull ID<String, String> INDEX_ID = ID.create("dummy.fbi.extension");

    @Override
    public @NotNull ID<String, String> getName() {
      return INDEX_ID;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
      return fc -> Collections.singletonMap(fc.getFileName(), fc.getFileName());
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
      return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
      return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
      return VERSION;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
      return f -> f.getName().equals("file" + VERSION + ".java");
    }

    @Override
    public boolean dependsOnFileContent() {
      return true;
    }
  }

}
