// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param, boolean b) {
      return <caret>switch (param) {
          case 1:
              if (b) {
                  throw new RuntimeException("one");
              }
          default:
              throw new RuntimeException("default");
      };
  }
}