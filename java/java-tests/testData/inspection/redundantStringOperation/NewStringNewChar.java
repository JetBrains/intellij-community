class Test {
  String foo1(int i) {
    return new String(new char[] { <error descr="Incompatible types. Found: 'int', required: 'char'">i</error> });
  }

  String foo2(char c) {
    return new String(new byte[] { <error descr="Incompatible types. Found: 'char', required: 'byte'">c</error> });
  }

  String foo3(char c1, char c2) {
    return new String(new char[] { c1, c2 });
  }
}
