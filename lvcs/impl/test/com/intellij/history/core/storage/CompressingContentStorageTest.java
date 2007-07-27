package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcsTestCase;
import org.easymock.IAnswer;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.io.*;

public class CompressingContentStorageTest extends LocalVcsTestCase {
  @Test
  public void testDelegation() throws IOException {
    IContentStorage subject = createStrictMock(IContentStorage.class);

    subject.remove(1);
    subject.close();

    replay(subject);

    CompressingContentStorage s = new CompressingContentStorage(subject);

    s.remove(1);
    s.close();

    verify(subject);
  }

  @Test
  public void testCompressionAndDecompression() throws IOException {
    final byte[][] compressed = new byte[1][];
    IContentStorage subject = createStoredBytesRecordingMock(compressed);

    byte[] original = "public void foo() {} public void foo() {}".getBytes();

    CompressingContentStorage s = new CompressingContentStorage(subject);
    assertEquals(1, s.store(original));

    assertTrue(compressed[0].length < original.length);

    reset(subject);
    expect(subject.load(2)).andReturn(compressed[0]);
    replay(subject);

    assertArrayEquals(original, s.load(2));
  }

  @Test
  public void testClosingOfInputAndOutputStreams() throws IOException {
    IContentStorage subject = createStrictMock(IContentStorage.class);
    expect(subject.store((byte[])anyObject())).andReturn(1);
    expect(subject.load(anyInt())).andReturn(new byte[0]);
    replay(subject);

    final boolean[] closeCalled = new boolean[2];

    CompressingContentStorage s = new CompressingContentStorage(subject) {
      @Override
      protected OutputStream createDeflaterOutputStream(OutputStream s) {
        return new DataOutputStream(s) {
          @Override
          public void close() throws IOException {
            closeCalled[0] = true;
            super.close();
          }
        };
      }

      @Override
      protected InputStream createInflaterOutputStream(byte[] content) {
        return new ByteArrayInputStream(content) {
          @Override
          public void close() throws IOException {
            closeCalled[1] = true;
            super.close();
          }
        };
      }
    };

    s.store(new byte[0]);
    s.load(0);

    assertTrue(closeCalled[0]);
    assertTrue(closeCalled[1]);
  }

  private IContentStorage createStoredBytesRecordingMock(final byte[][] compressed) throws IOException {
    IContentStorage subject = createMock(IContentStorage.class);

    expect(subject.store((byte[])anyObject())).andAnswer(new IAnswer<Integer>() {
      public Integer answer() throws Throwable {
        compressed[0] = (byte[])getCurrentArguments()[0];
        return 1;
      }
    });

    replay(subject);
    return subject;
  }
}