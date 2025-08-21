// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      return a() + switch<caret> (param) {
          default -> throw new RuntimeException("default");
      };
  }
  int a() {return 0;}
}