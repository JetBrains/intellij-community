// "Create local variable 'x2'" "true-preview"

class A {
    public void foo() {
        int x2;
        var x = switch (x2)
        {
          case 1 ->
          {
            yield "bar";
          }
          default -> "foo";
        };
    }
}