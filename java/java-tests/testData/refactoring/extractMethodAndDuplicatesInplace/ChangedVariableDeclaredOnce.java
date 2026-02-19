public class Test {
    void test(boolean condition) {
        <selection>String s = "42";</selection>
        if (condition) s = "new";
        System.out.println(s);
    }
}