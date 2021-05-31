class Test {
  String foo1(int i) {
    return String.valueOf(new char[] { <error descr="Incompatible types. Found: 'int', required: 'char'">i</error> });
  }

  String foo2(char c) {
    return String.valueOf(new byte[] { <error descr="Incompatible types. Found: 'char', required: 'byte'">c</error> });
  }

  String foo3(char c1, char c2) {
    return String.valueOf(new char[] { c1, c2 });
  }
}
