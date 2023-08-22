// "Create local variable 'x2'" "true-preview"

import static A.Month.APRIL;

class A {
    public void foo() {
      var x = switch (x2<caret>)
        {
          case APRIL ->
          {
            yield "bar";
          }
          default -> "foo";
        };
    }
    enum Month{APRIL, MAY};
}