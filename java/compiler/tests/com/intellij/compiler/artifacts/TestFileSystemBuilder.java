package com.intellij.compiler.artifacts;

/**
 * @author nik
 */
public class TestFileSystemBuilder {
  private TestFileSystemItem myItem;
  private TestFileSystemBuilder myParent;

  public TestFileSystemBuilder(TestFileSystemItem item, TestFileSystemBuilder parent) {
    myItem = item;
    myParent = parent;
  }

  public TestFileSystemItem build() {
    TestFileSystemBuilder builder = this;
    while (builder.myParent != null) {
      builder = builder.myParent;
    }
    return builder.myItem;
  }

  public TestFileSystemBuilder dir(String name) {
    final TestFileSystemItem item = new TestFileSystemItem(name, false, true);
    myItem.addChild(item);
    return new TestFileSystemBuilder(item, this);
  }

  public TestFileSystemBuilder archive(String name) {
    final TestFileSystemItem item = new TestFileSystemItem(name, true, false);
    myItem.addChild(item);
    return new TestFileSystemBuilder(item, this);
  }

  public TestFileSystemBuilder file(String name) {
    myItem.addChild(new TestFileSystemItem(name, false, false));
    return this;
  }

  public TestFileSystemBuilder file(String name, String content) {
    myItem.addChild(new TestFileSystemItem(name, false, false, content));
    return this;
  }

  public TestFileSystemBuilder end() {
    return myParent;
  }
}
