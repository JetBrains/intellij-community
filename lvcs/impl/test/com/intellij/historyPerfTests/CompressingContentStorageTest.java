package com.intellij.historyPerfTests;

import com.intellij.idea.Bombed;
import com.intellij.history.core.storage.CompressingContentStorage;
import com.intellij.history.core.storage.IContentStorage;
import com.intellij.history.utils.RunnableAdapter;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;

@Bombed(month = Calendar.NOVEMBER, day = 31, user = "anton")
public class CompressingContentStorageTest extends PerformanceTestCase {
  CompressingContentStorage s;

  @Before
  public void setUp() {
    s = new CompressingContentStorage(new MyContentStorage());
  }

  @Test
  public void testCompression() throws IOException {
    assertExecutionTime(300, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.store("hello, world".getBytes());
        }
      }
    });
  }

  @Test
  public void testDecompression() throws IOException {
    s.store("hello, world".getBytes());

    assertExecutionTime(140, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.load(0);
        }
      }
    });
  }

  class MyContentStorage implements IContentStorage {
    byte[] myContent;

    public int store(byte[] content) throws IOException {
      myContent = content;
      return 0;
    }

    public byte[] load(int id) throws IOException {
      return myContent;
    }

    public void close() {
      throw new UnsupportedOperationException();
    }

    public void save() {
      throw new UnsupportedOperationException();
    }

    public void remove(int id) {
      throw new UnsupportedOperationException();
    }

    public boolean isRemoved(int id) {
      throw new UnsupportedOperationException();
    }
  }
}