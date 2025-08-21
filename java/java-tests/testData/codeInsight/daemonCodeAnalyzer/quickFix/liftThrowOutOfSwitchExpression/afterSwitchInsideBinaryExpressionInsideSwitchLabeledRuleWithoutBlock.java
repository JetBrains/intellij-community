// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        switch (param) {
            default -> {
                call();
                throw <caret>switch (param) {
                    default -> new ArithmeticException();
                };
            }
        }
    }
    public int call() {
      return 3;
    }
}