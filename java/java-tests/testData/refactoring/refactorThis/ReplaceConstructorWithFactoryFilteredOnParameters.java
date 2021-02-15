class A {

    A(String arg) {}

    void test(char[] chars) {
      String s = new A("chars"<caret>).toString();
    }
}