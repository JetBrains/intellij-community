// "Remove switch branch 'Day dd && true'" "true-preview"
enum Day {
    MONDAY, TUESDAY, WEDNESDAY
}

class Test {
    int foo(Day d) {
        switch (d) {
            case <caret>Day dd && true:
                System.out.println(42);
                break;
            case default:
                System.out.println(13);
        }
    }
}