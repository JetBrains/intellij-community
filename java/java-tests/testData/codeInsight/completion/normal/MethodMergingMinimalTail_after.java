class Foo {
    {
      foo("someTestAttachment", "".getBytes(<caret>))
    }

  void foo(String s, byte[] z) {}
}