// "Lift 'throw' out of 'switch' expression" "true-preview"

public class Foo {
    public int bar(int param) {
        if (param==2)
            int x = 3, y = <caret>switch (param) {
                default -> throw new ArithmeticException();
            };
        else {
            return 1;
        }
    }
    public int call(int param) {
      return 3;
    }
}