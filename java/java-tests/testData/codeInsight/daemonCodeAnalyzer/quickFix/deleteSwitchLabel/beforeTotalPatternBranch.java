// "Remove switch branch 'Day dd when true'" "true-preview"
enum Day {
    MONDAY, TUESDAY, WEDNESDAY
}

class Test {
    int foo(Day d) {
        switch (d) {
            case <caret>Day dd when true:
                System.out.println(42);
                break;
            default:
                System.out.println(13);
        }
    }
}