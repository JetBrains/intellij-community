// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
    int bar(int param) {
        throw switch (param) {
            default:
                switch (param) {
                    case 1 -> {
                        yield new RuntimeException();
                    }
                    default -> {
                        yield new RuntimeException();
                    }
                }
        };
    }
}