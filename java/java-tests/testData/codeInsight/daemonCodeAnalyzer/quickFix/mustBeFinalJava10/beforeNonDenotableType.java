// "Move 'x' into anonymous object" "false"
class Test {
    void test() {
        Integer i = 1;
        var x = (Number & Comparable<?>)i;
        Runnable r = () -> {
            x<caret> = 2;
        };
        System.out.println(x.compareTo(null));
    }
}
