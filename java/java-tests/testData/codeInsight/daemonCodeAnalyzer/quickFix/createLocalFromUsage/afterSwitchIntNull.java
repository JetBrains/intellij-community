// "Create local variable 'x2'" "true-preview"

class A {
    public void foo() {
        Integer x2;
        var x = switch (x2)
        {
          case 1 ->
          {
            yield "bar";
          }
          case null -> "null";
          default -> "foo";
        };
    }
}