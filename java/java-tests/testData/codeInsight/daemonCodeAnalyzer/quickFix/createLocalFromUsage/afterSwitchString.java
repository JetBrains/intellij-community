// "Create local variable 'x2'" "true-preview"

class A {
    public void foo() {
        String x2;
        var x = switch (x2)
        {
          case "bar" ->
          {
            yield "bar";
          }
          default -> "foo";
        };
    }
}