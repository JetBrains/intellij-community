// "Remove switch branch 'default'" "true-preview"
enum Day {
    MONDAY, TUESDAY, WEDNESDAY
}

class Test {
    int foo(Day d) {
        return switch (d) {
            case Day dd && true -> 42;
        };
    }
}