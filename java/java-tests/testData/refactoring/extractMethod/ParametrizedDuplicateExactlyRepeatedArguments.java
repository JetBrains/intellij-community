class C {
  void foo(String s, StringBuilder b) {
    b.append("repeated");
    b.append("repeated");
    b.append("repeated");

    <selection>
    b.append("a");
    b.append("a");
    b.append("a");
    </selection>
    b.append("d");
  }
}