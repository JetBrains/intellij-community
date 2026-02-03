// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        switch (param) {
            default -> {
                throw <caret>switch (param) {
                    default -> new ArithmeticException();
                };
            }
        }
    }
}