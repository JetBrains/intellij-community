// "Replace '(String) aText' with 'anActualText'" "true"

class FooBar {
  void method() {
    String anActualText = "Hello World! ";
    Object aText = anActualText;
    System.out.println(((String<caret>) aText).trim());
    aText = Integer.MAX_VALUE;
  }
}