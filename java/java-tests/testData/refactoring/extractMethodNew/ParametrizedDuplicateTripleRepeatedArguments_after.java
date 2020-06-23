class C {
  void foo(String s, StringBuilder b) {
      newMethod(b, "repeated", "repeated", s, "repeated");


      newMethod(b, "a", "b", "b", "c");

      b.append("d");
  }

    private void newMethod(StringBuilder b, String a, String b2, String b3, String c) {
        b.append(a);
        b.append(b2);
        b.append(b3);
        b.append(c);
    }
}