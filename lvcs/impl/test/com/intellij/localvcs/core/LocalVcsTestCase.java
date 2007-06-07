package com.intellij.localvcs.core;

import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.storage.ByteContent;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.Clock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public abstract class LocalVcsTestCase extends Assert {
  private Locale myDefaultLocale;
  private int myCurrentEntryId = 1;

  @Before
  public void setUpLocale() {
    myDefaultLocale = Locale.getDefault();
    Locale.setDefault(new Locale("ru", "RU"));
  }

  @After
  public void restoreLocale() {
    Locale.setDefault(myDefaultLocale);
  }

  protected int getNextId() {
    return myCurrentEntryId++;
  }

  protected static byte[] b(String s) {
    return s.getBytes();
  }

  protected static Content c(String data) {
    return new ByteContent(b(data));
  }

  public static ContentFactory cf(String data) {
    return createContentFactory(b(data));
  }

  public static ContentFactory bigContentFactory() {
    return createContentFactory(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);
  }

  private static ContentFactory createContentFactory(final byte[] bytes) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() {
        return bytes;
      }

      @Override
      public long getLength() {
        return bytes.length;
      }
    };
  }

  protected static <T> T[] list(T... objects) {
    return objects;
  }

  protected static IdPath idp(int... parts) {
    return new IdPath(parts);
  }

  protected static ChangeSet cs(Change... changes) {
    return cs(null, changes);
  }

  protected static ChangeSet cs(String name, Change... changes) {
    return cs(0, name, changes);
  }

  protected static ChangeSet cs(long timestamp, Change... changes) {
    return cs(timestamp, null, changes);
  }

  protected static ChangeSet cs(long timestamp, String name, Change... changes) {
    return new ChangeSet(timestamp, name, Arrays.asList(changes));
  }

  protected void createFile(Entry r, int id, String path, Content c, long timestamp) {
    new CreateFileChange(id, path, c, timestamp).applyTo(r);
  }

  protected void createFile(Entry r, String path, Content c, long timestamp) {
    createFile(r, getNextId(), path, c, timestamp);
  }

  protected void createDirectory(Entry r, int id, String path) {
    new CreateDirectoryChange(id, path).applyTo(r);
  }

  protected void createDirectory(Entry r, String path) {
    createDirectory(r, getNextId(), path);
  }

  protected void changeFileContent(Entry r, String path, Content c, long timestamp) {
    new ChangeFileContentChange(path, c, timestamp).applyTo(r);
  }

  protected void rename(Entry r, String path, String newName) {
    new RenameChange(path, newName).applyTo(r);
  }

  protected void move(Entry r, String path, String newParent) {
    new MoveChange(path, newParent).applyTo(r);
  }

  protected void delete(Entry r, String path) {
    new DeleteChange(path).applyTo(r);
  }

  protected void setCurrentTimestamp(long t) {
    Clock.setCurrentTimestamp(t);
  }

  protected static void assertEquals(Object[] expected, Collection actual) {
    assertEquals(expected, actual.toArray());
  }

  protected static void assertEquals(byte[] expected, byte[] actual) {
    String message = notEqualsMessage(expected, actual);

    assertTrue(message, expected.length == actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertTrue(message, expected[i] == actual[i]);
    }
  }

  protected void assertEquals(long expected, long actual) {
    Assert.assertEquals(expected, actual);
  }

  private static String notEqualsMessage(Object expected, Object actual) {
    return "elements are not equal:\n" + "\texpected: " + expected + "\n" + "\tactual: " + actual;
  }
}
