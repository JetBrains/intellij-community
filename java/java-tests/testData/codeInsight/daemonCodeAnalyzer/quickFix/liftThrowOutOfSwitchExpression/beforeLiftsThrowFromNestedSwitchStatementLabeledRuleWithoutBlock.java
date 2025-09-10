// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
    int bar(int param) {
        return <caret>switch (param) {
            default:
                switch (param) {
                    case 1 -> throw new RuntimeException();
                    default -> throw new RuntimeException();
                }
        };
    }
}