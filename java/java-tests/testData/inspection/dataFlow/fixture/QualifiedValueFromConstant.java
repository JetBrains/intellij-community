class Scratch {
  void test(String codeblock) {
    if (codeblock.trim().equals("{")) {
      if (<warning descr="Condition 'codeblock.equals(\"}\")' is always 'false'">codeblock.equals("}")</warning>) {
        System.out.println(codeblock.trim());
      }
    }
  }

  enum E {A, B, C}

  void test(E e) {
    if (e.name().equals("C")) {
      if (<warning descr="Condition 'e == E.A' is always 'false'">e == E.A</warning>) {
        System.out.println(e.name());
      }
    }
  }
}