// "Remove switch label 'default'" "true-preview"
enum Day {
    MONDAY, TUESDAY, WEDNESDAY
}

class Test {
    int foo(Day d) {
        return switch (d) {
            case MONDAY -> 42;
            case Day dd -> 13;
        };
    }
}