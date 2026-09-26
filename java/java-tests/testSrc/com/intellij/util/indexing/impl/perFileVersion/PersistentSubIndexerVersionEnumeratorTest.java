// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.indexing.CompositeDataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.IndexedFileImpl;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@SuppressWarnings("FieldMayBeStatic")
public class PersistentSubIndexerVersionEnumeratorTest extends LightJavaCodeInsightFixtureTestCase {
  private TempDirTestFixture myDirTestFixture;
  private Path myRoot;

  private PersistentSubIndexerRetriever<MyIndexFileAttribute, String> myMap;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRoot = FileUtil.createTempDirectory("persistent", "map").toPath();
    myDirTestFixture = new TempDirTestFixtureImpl();
    myDirTestFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myMap != null) {
        myMap.close();
      }
      myDirTestFixture.tearDown();
    }
    catch (Exception e) {
      addSuppressedException(e);
    } finally {
      super.tearDown();
    }
  }

  public void testAddRetrieve() throws IOException {
    myMap = new PersistentSubIndexerRetriever<>(myRoot, "index_name", 0, new MyPerFileIndexExtension());
    persistIndexedState(foo1);
    persistIndexedState(boo1);

    assertTrue(isIndexedState(foo1));
    assertTrue(isIndexedState(boo1));

    assertFalse(isIndexedState(foo2));
    assertFalse(isIndexedState(baz1));
  }

  public void testInvalidation() throws IOException {
    myMap = new PersistentSubIndexerRetriever<>(myRoot, "index_name", 0, new MyPerFileIndexExtension());
    persistIndexedState(foo1);
    assertTrue(isIndexedState(foo1));
    myMap.close();
    myMap = new PersistentSubIndexerRetriever<>(myRoot, "index_name", 0, new MyPerFileIndexExtension());
    assertFalse(isIndexedState(foo2));
    persistIndexedState(foo2);
    assertTrue(isIndexedState(foo2));
    assertFalse(isIndexedState(foo1));
  }

  public void testStaleKeysRemoval() {
    int baseThreshold = PersistentSubIndexerVersionEnumerator.getStorageSizeLimit();
    PersistentSubIndexerVersionEnumerator.setStorageSizeLimit(2);
    try {
      myMap = new PersistentSubIndexerRetriever<>(myRoot, "index_name", 0, new MyPerFileIndexExtension());
      persistIndexedState(foo1);
      persistIndexedState(boo1);
      persistIndexedState(bar1);
      persistIndexedState(baz1);

      assertTrue(isIndexedState(foo1));
      assertTrue(isIndexedState(boo1));
      assertTrue(isIndexedState(bar1));
      assertTrue(isIndexedState(baz1));

      myMap.close();
      myMap = new PersistentSubIndexerRetriever<>(myRoot, "index_name", 0, new MyPerFileIndexExtension());
      fail();
    } catch (IOException ignored) {
      // it's ok
    } finally {
      PersistentSubIndexerVersionEnumerator.setStorageSizeLimit(baseThreshold);
    }

  }

  private static final Key<MyIndexFileAttribute> ATTRIBUTE_KEY = Key.create("my.index.attr.key");

  private static final class MyPerFileIndexExtension implements CompositeDataIndexer<String, String, MyIndexFileAttribute, String> {
    @Nullable
    @Override
    public MyIndexFileAttribute calculateSubIndexer(@NotNull IndexedFile file) {
      return file.getFile().getUserData(ATTRIBUTE_KEY);
    }

    @NotNull
    @Override
    public String getSubIndexerVersion(@NotNull MyIndexFileAttribute attribute) {
      return attribute.name + ":" + attribute.version;
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getSubIndexerVersionDescriptor() {
      return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public Map<String, String> map(@NotNull FileContent inputData, @NotNull MyIndexFileAttribute attribute) {
      return null;
    }
  }

  private final MyIndexFileAttribute foo1 = new MyIndexFileAttribute(1, "foo");
  private final MyIndexFileAttribute foo2 = new MyIndexFileAttribute(2, "foo");
  private final MyIndexFileAttribute foo3 = new MyIndexFileAttribute(3, "foo");

  private final MyIndexFileAttribute bar1 = new MyIndexFileAttribute(1, "bar");
  private final MyIndexFileAttribute bar2 = new MyIndexFileAttribute(2, "bar");

  private final MyIndexFileAttribute baz1 = new MyIndexFileAttribute(1, "baz");

  private final MyIndexFileAttribute boo1 = new MyIndexFileAttribute(1, "boo");

  private static final class MyIndexFileAttribute {
    final int version;
    final String name;

    private MyIndexFileAttribute(int version, String name) {
      this.version = version;
      this.name = name;
    }
  }

  @NotNull
  VirtualFile file(@NotNull MyIndexFileAttribute attribute) {
    return myDirTestFixture.createFile(attribute.name + ".java");
  }

  void persistIndexedState(@NotNull MyIndexFileAttribute attribute) {
    VirtualFile file = file(attribute);
    file.putUserData(ATTRIBUTE_KEY, attribute);
    try {
      myMap.setIndexedState(((VirtualFileWithId) file).getId(), new IndexedFileImpl(file, getProject()));
    }
    catch (IOException e) {
      LOG.error(e);
      fail(e.getMessage());
    } finally {
      file.putUserData(ATTRIBUTE_KEY, null);
    }
  }

  boolean isIndexedState(@NotNull MyIndexFileAttribute attribute) {
    VirtualFile file = file(attribute);
    file.putUserData(ATTRIBUTE_KEY, attribute);
    try {
      return myMap.getSubIndexerState(((VirtualFileWithId)file).getId(), new IndexedFileImpl(file, getProject())).isUpToDate();
    }
    catch (IOException e) {
      LOG.error(e);
      fail(e.getMessage());
      return false;
    } finally {
      file.putUserData(ATTRIBUTE_KEY, null);
    }
  }
}

