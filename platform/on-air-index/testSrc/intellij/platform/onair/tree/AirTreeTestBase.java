// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import static intellij.platform.onair.tree.TestByteUtils.writeUnsignedInt;

public abstract class AirTreeTestBase {
  private static final DecimalFormat FORMATTER;

  static {
    FORMATTER = (DecimalFormat)NumberFormat.getIntegerInstance();
    FORMATTER.applyPattern("00000");
  }

  Storage storage = null;
  MockNovelty novelty = null;

  @Before
  public void setUp() {
    storage = new MockStorage();
    novelty = new MockNovelty();
  }

  @After
  public void tearDown() {
    storage = null;
    novelty = null;
  }

  @NotNull
  protected BTree reopen(BTree tree) {
    Address address = tree.store(novelty);
    novelty = new MockNovelty(); // cleanup
    tree = BTree.load(storage, 4, address);
    return tree;
  }

  @NotNull
  public BTree createTree() {
    return BTree.create(novelty, storage, 4);
  }

  public static byte[] key(int key) {
    byte[] result = new byte[4];
    writeUnsignedInt(key ^ 0x80000000, result);
    return result;
  }

  public static byte[] v(int value) {
    return key("val " + FORMATTER.format(value));
  }

  private static byte[] key(String key) {
    return key == null ? null : key.getBytes(Charset.forName("UTF-8"));
  }

  public static byte[] value(String value) {
    return key(value);
  }
}
