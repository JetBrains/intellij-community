class Foo {
  String getAttributeValue(String s);
  String getNamespace();
}

class Bar {
  String getAttribute() {
    Foo f;
    return f.<caret>
  }
}