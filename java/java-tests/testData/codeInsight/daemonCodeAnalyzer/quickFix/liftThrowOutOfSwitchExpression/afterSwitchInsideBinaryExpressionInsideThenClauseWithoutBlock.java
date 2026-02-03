// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        if (param==2) {
            call(1);
            throw <caret>switch (param) {
                default -> new ArithmeticException();
            };
        } else {
            return 1;
        }
    }
    public int call(Object... param) {
        return 3;
    }
}