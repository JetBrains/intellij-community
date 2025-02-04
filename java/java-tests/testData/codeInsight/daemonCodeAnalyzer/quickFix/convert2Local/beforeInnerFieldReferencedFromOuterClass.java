// "Fix all 'Field can be local variable' problems in file" "false"
class Outer {

  void test(Inner inner) {
    System.out.println(inner.field);
  }

  class Inner {
    private final String f<caret>ield;

    Inner(String field) {
      this.field = field;
    }
  }
}