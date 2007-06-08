package com.intellij.localvcsperf;

import com.intellij.idea.Bombed;
import com.intellij.localvcs.core.storage.ContentStorage;
import com.intellij.localvcs.utils.RunnableAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Bombed(month = Calendar.AUGUST, day = 31, user = "anton")
public class ContentStorageTest extends PerformanceTestCase {
  int ITERATIONS_COUNT = 1000;
  int MAX_RECORD_SIZE = 20 * 1024;

  File sf;
  ContentStorage s;

  @Before
  public void setUp() throws Exception {
    sf = new File(tempDir, "s");
    s = new ContentStorage(sf);
  }

  @After
  public void tearDown() {
    s.close();
  }

  @Test
  public void testStorageWriting() throws Exception {
    assertExecutionTime(1000, new RunnableAdapter() {
      public void doRun() throws Exception {
        createContentsOfDifferentSize();
      }
    });
  }

  @Test
  public void testStorageReading() throws Exception {
    final List<Integer> cc = createContentsOfDifferentSize();
    assertExecutionTime(50, new RunnableAdapter() {
      public void doRun() throws Exception {
        readContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testStorageDeletion() throws Exception {
    final List<Integer> cc = createContentsOfDifferentSize();
    assertExecutionTime(15, new RunnableAdapter() {
      public void doRun() throws Exception {
        deleteHalfOfContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testStorageWritingAfterDeletion() throws Exception {
    List<Integer> cc = createContentsOfDifferentSize();
    deleteHalfOfContentsRandomly(cc);

    assertExecutionTime(1500, new RunnableAdapter() {
      public void doRun() throws Exception {
        createContentsOfDifferentSize();
      }
    });
  }

  @Test
  public void testStorageReadingAfterManyModifications() throws IOException {
    final List<Integer> cc = modifyStorageManyTimes();

    assertExecutionTime(30, new RunnableAdapter() {
      public void doRun() throws Exception {
        readContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testStorageSizeOfterManyModifications() throws IOException {
    modifyStorageManyTimes();
    assertEquals(15, (int)sf.length() / (1024 * 1024));
  }

  private List<Integer> createContentsOfDifferentSize() throws IOException {
    List<Integer> result = new ArrayList<Integer>();
    for (int i = 0; i < ITERATIONS_COUNT; i++) {
      result.add(s.store(arrayOfSize(randomSize())));
    }
    s.save();
    return result;
  }

  private byte[] arrayOfSize(int size) {
    byte[] bb = new byte[size];
    for (int i = 0; i < size; i++) bb[i] = (byte)i;
    return bb;
  }

  private void readContentsRandomly(List<Integer> cc) throws IOException {
    for (int i = 0; i < ITERATIONS_COUNT; i++) {
      s.load(randomItem(cc));
    }
  }

  private void deleteHalfOfContentsRandomly(List<Integer> cc) throws IOException {
    int half = cc.size() / 2;
    for (int i = 0; i < half; i++) {
      Integer item = randomItem(cc);
      s.remove(item);
      cc.remove(item);
    }
  }

  private List<Integer> modifyStorageManyTimes() throws IOException {
    List<Integer> cc = createContentsOfDifferentSize();
    deleteHalfOfContentsRandomly(cc);
    cc.addAll(createContentsOfDifferentSize());
    return cc;
  }

  private int randomSize() {
    return rand(MAX_RECORD_SIZE) + 1;
  }

  private Integer randomItem(List<Integer> rr) {
    return rr.get(rand(rr.size()));
  }
}
