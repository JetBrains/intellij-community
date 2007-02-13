package com.intellij.localvcsperf;

import com.intellij.util.io.PagedMemoryMappedFile;
import com.intellij.util.io.RecordDataOutput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class PagedFileTest extends PerformanceTest {
  PagedMemoryMappedFile f;

  @Before
  public void setUp() throws Exception {
    f = new PagedMemoryMappedFile(new File(tempDir, "file"));
  }

  @After
  public void tearDown() {
    f.dispose();
  }

  @Test
  public void testWritingChunksOfDifferentSizes() throws Exception {
    assertExecutionTime(1, new Task() {
      public void execute() throws Exception {
        for (int i = 0; i < 1000; i++) {
          RecordDataOutput r = f.createRecord();
          r.write(new byte[1024 * rand(50) + 1]);
          r.close();
        }
      }
    });
  }
}
