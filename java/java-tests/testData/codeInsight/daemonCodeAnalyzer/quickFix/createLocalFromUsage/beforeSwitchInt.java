// "Create local variable 'x2'" "true-preview"

class A {
    public void foo() {
      var x = switch (x2<caret>)
        {
          case 1 ->
          {
            yield "bar";
          }
          default -> "foo";
        };
    }
}