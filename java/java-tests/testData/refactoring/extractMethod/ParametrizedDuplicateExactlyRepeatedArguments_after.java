class C {
  void foo(String s, StringBuilder b) {
      newMethod(b, "repeated");


      newMethod(b, "a");

      b.append("d");
  }

    private void newMethod(StringBuilder b, String a) {
        b.append(a);
        b.append(a);
        b.append(a);
    }
}