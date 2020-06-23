class C {
  void foo(String s, StringBuilder b) {
      newMethod(b, "repeated", s, "repeated");


      newMethod(b, "a", "b", "c");

      b.append("d");
  }

    private void newMethod(StringBuilder b, String a, String b2, String c) {
        b.append(a);
        b.append(b2);
        b.append(c);
    }
}