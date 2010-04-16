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

package com.intellij.history.core.storage;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.List;

public class StreamTest extends LocalHistoryTestCase {
  private DataInputStream is;
  private DataOutputStream os;

  @Before
  public void setUpStreams() throws IOException {
    PipedOutputStream pos = new PipedOutputStream();
    os = new DataOutputStream(pos);
    is = new DataInputStream(new PipedInputStream(pos));
  }

  @Test
  public void testString() throws Exception {
    StreamUtil.writeString(os, "hello");
    assertEquals("hello", StreamUtil.readString(is));
  }

  @Test
  public void testStringOrNull() throws Exception {
    StreamUtil.writeStringOrNull(os, "hello");
    StreamUtil.writeStringOrNull(os, null);
    assertEquals("hello", StreamUtil.readStringOrNull(is));
    assertNull(StreamUtil.readStringOrNull(is));
  }

  @Test
  public void testFileEntry() throws Exception {
    Entry e = new FileEntry("file", new StoredContent(333), 123L, true);

    StreamUtil.writeEntry(os, e);
    Entry result = StreamUtil.readEntry(is);

    assertEquals(FileEntry.class, result.getClass());

    assertEquals("file", result.getName());
    assertEquals(333, ((StoredContent)result.getContent()).getContentId());
    assertEquals(123L, result.getTimestamp());
    assertTrue(result.isReadOnly());
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    Entry parent = new DirectoryEntry("");
    Entry e = new FileEntry("", new StoredContent(333), -1, false);

    parent.addChild(e);
    StreamUtil.writeEntry(os, e);

    assertNull(StreamUtil.readEntry(is).getParent());
  }

  @Test
  public void testEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry("name");

    StreamUtil.writeEntry(os, e);
    Entry result = StreamUtil.readEntry(is);

    assertEquals(DirectoryEntry.class, result.getClass());

    assertEquals("name", result.getName());
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    Entry dir = new DirectoryEntry("");
    Entry subDir = new DirectoryEntry("");
    dir.addChild(subDir);
    subDir.addChild(new FileEntry("a", new StoredContent(333), -1, false));
    subDir.addChild(new FileEntry("b", new StoredContent(333), -1, false));

    StreamUtil.writeEntry(os, dir);
    Entry result = StreamUtil.readEntry(is);

    List<Entry> children = result.getChildren();
    assertEquals(1, children.size());

    Entry e = children.get(0);
    assertEquals(DirectoryEntry.class, e.getClass());
    assertSame(result, e.getParent());

    children = e.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertSame(e, children.get(0).getParent());

    assertEquals(FileEntry.class, children.get(1).getClass());
    assertSame(e, children.get(1).getParent());
  }

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(nextId(), "file");

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(CreateFileChange.class, read.getClass());
    CreateFileChange result = (CreateFileChange)read;

    assertEquals("file", result.getPath());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(nextId(), "dir");

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(CreateDirectoryChange.class, read.getClass());
    CreateDirectoryChange result = (CreateDirectoryChange)read;

    assertEquals("dir", result.getPath());
  }

  @Test
  public void testContentChange() throws IOException {
    Change c = new ContentChange(nextId(), "file", new StoredContent(333), 2L);

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(ContentChange.class, read.getClass());
    ContentChange result = (ContentChange)read;

    assertEquals("file", result.getPath());
    assertEquals(333, ((StoredContent)result.getOldContent()).getContentId());
    assertEquals(2L, result.getOldTimestamp());
  }

  @Test
  public void testDeleteChange() throws IOException {
    DirectoryEntry  dir = new DirectoryEntry("dir");
    dir.addChild(new FileEntry("file", new StoredContent(333), -1, false));
    dir.addChild(new DirectoryEntry("subDir"));

    Change c = new DeleteChange(nextId(), "entry", dir);

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(DeleteChange.class, read.getClass());
    DeleteChange result = (DeleteChange)read;

    assertEquals("entry", result.getPath());

    Entry e = result.getDeletedEntry();

    assertEquals(DirectoryEntry.class, e.getClass());
    assertEquals("dir", e.getName());

    assertEquals(2, e.getChildren().size());
    assertEquals("dir/file", e.getChildren().get(0).getPath());
    assertEquals("dir/subDir", e.getChildren().get(1).getPath());
  }

  @Test
  public void testRenameChange() throws IOException {
    Change c = new RenameChange(nextId(), "new name", "old name");

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(RenameChange.class, read.getClass());
    RenameChange result = ((RenameChange)read);

    assertEquals("new name", result.getPath());
    assertEquals("old name", result.getOldName());
  }

  @Test
  public void testROStatusChange() throws IOException {
    Entry root = new RootEntry();

    Change c = new ROStatusChange(nextId(), "f", true);

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(ROStatusChange.class, read.getClass());
    ROStatusChange result = ((ROStatusChange)read);

    assertEquals("f", result.getPath());
    assertEquals(true, result.getOldStatus());
  }

  @Test
  public void testMoveChange() throws IOException {
    Change c = new MoveChange(nextId(), "dir2/file", "dir1");

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(MoveChange.class, read.getClass());
    MoveChange result = ((MoveChange)read);

    assertEquals("dir1/file", result.getOldPath());
    assertEquals("dir2/file", result.getPath());
  }

  @Test
  public void testPutLabelChange() throws IOException {
    Change c = new PutLabelChange(nextId(), "name", "projectId");

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(PutLabelChange.class, read.getClass());
    assertEquals("name", ((PutLabelChange)read).getName());
    assertEquals("projectId", ((PutLabelChange)read).getProjectId());
  }

  @Test
  public void testPutSystemLabelChange() throws IOException {
    Change c = new PutSystemLabelChange(nextId(), "name", "projectId", 123);

    StreamUtil.writeChange(os, c);
    Change read = StreamUtil.readChange(is);

    assertEquals(PutSystemLabelChange.class, read.getClass());
    assertEquals("name", ((PutSystemLabelChange)read).getName());
    assertEquals("projectId", ((PutSystemLabelChange)read).getProjectId());
    assertEquals(123, ((PutSystemLabelChange)read).getColor());
  }

  @Test
  public void testChangeSet() throws IOException {
    ChangeSet cs = cs(123, "name", new CreateFileChange(nextId(), "file"));

    cs.write(os);
    ChangeSet read = new ChangeSet(is);

    ChangeSet result = read;

    assertEquals("name", read.getName());
    assertEquals(123L, read.getTimestamp());
    assertEquals(1, result.getChanges().size());
    assertEquals(CreateFileChange.class, result.getChanges().get(0).getClass());
  }

  @Test
  public void testChangeSetWithoutName() throws IOException {
    ChangeSet cs = cs((String)null);

    cs.write(os);
    ChangeSet read = new ChangeSet(is);

    assertNull(read.getName());
  }
}
