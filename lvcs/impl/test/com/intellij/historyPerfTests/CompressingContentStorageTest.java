package com.intellij.historyPerfTests;

import com.intellij.history.core.storage.CompressingContentStorage;
import com.intellij.history.core.storage.IContentStorage;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.idea.Bombed;
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
    assertExecutionTime(1600, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.store(bytesToStore());
        }
      }
    });
  }

  @Test
  public void testDecompression() throws IOException {
    s.store(bytesToStore());

    assertExecutionTime(650, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.load(0);
        }
      }
    });
  }

  private byte[] bytesToStore() {
    return ("package com.intellij.historyPerfTests;\n" + "\n" + "import com.intellij.history.core.storage.CompressingContentStorage;\n" +
            "import com.intellij.history.core.storage.IContentStorage;\n" + "import com.intellij.history.utils.RunnableAdapter;\n" +
            "import com.intellij.idea.Bombed;\n" + "import org.junit.Before;\n" + "import org.junit.Test;\n" + "\n" +
            "import java.io.IOException;\n" + "import java.util.Calendar;\n" + "\n" +
            "@Bombed(month = Calendar.NOVEMBER, day = 31, user = \"anton\")\n" +
            "public class CompressingContentStorageTest extends PerformanceTestCase {\n" + "  CompressingContentStorage s;\n" + "\n" +
            "  @Before\n" + "  public void setUp() {\n" + "    s = new CompressingContentStorage(new MyContentStorage());\n" + "  }\n" +
            "\n" + "  @Test\n" + "  public void testCompression() throws IOException {\n" +
            "    assertExecutionTime(300, new RunnableAdapter() {\n" + "      public void doRun() throws Exception {\n" +
            "        for (int i = 0; i < 10000; i++) {\n" + "          s.store(bytesToStore());\n" + "        }\n" + "      }\n" +
            "    });\n" + "  }\n" + "\n" + "  @Test\n" + "  public void testDecompression() throws IOException {\n" +
            "    s.store(bytesToStore());\n" + "\n" + "    assertExecutionTime(140, new RunnableAdapter() {\n" +
            "      public void doRun() throws Exception {\n" + "        for (int i = 0; i < 10000; i++) {\n" + "          s.load(0);\n" +
            "        }\n" + "      }\n" + "    });\n" + "  }\n" + "\n" + "  private byte[] bytesToStore() {\n" +
            "    return \"hello, world\".getBytes();\n" + "  }\n" + "\n" + "  class MyContentStorage implements IContentStorage {\n" +
            "    byte[] myContent;\n" + "\n" + "    public int store(byte[] content) throws IOException {\n" +
            "      myContent = content;\n" + "      return 0;\n" + "    }\n" + "\n" +
            "    public byte[] load(int id) throws IOException {\n" + "      return myContent;\n" + "    }\n" + "\n" +
            "    public void close() {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public void remove(int id) {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public int getVersion() {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public void setVersion(final int version) {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" +
            "  }\n" + "}").getBytes();
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

    public void remove(int id) {
      throw new UnsupportedOperationException();
    }

    public int getVersion() {
      throw new UnsupportedOperationException();
    }

    public void setVersion(final int version) {
      throw new UnsupportedOperationException();
    }
  }
}