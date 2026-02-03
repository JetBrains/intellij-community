class C {
  void foo(String s, StringBuilder b) {
    b.append("repeated");
    b.append(s);
    b.append("repeated");

    <selection>
    b.append("a");
    b.append("b");
    b.append("c");
    </selection>
    b.append("d");
  }
}