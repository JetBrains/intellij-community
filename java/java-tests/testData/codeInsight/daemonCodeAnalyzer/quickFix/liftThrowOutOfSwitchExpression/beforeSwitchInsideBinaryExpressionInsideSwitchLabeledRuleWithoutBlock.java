// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        switch (param) {
            default -> call() + <caret>switch (param) {
                    default -> throw new ArithmeticException();
            };
        }
    }
    public int call() {
      return 3;
    }
}