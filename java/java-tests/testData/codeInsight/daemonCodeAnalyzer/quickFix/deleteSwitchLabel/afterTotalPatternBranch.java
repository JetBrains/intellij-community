// "Remove switch branch 'Day dd && true'" "true-preview"
enum Day {
    MONDAY, TUESDAY, WEDNESDAY
}

class Test {
    int foo(Day d) {
        switch (d) {
            case default:
                System.out.println(13);
        }
    }
}