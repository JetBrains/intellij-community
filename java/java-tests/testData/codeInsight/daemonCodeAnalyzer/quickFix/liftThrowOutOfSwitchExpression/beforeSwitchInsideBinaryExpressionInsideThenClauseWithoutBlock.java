// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        if (param==2)
            return call(call(1), <caret>switch (param) {
                default -> throw new ArithmeticException();
            });
        else {
            return 1;
        }
    }
    public int call(Object... param) {
        return 3;
    }
}