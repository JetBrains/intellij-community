import java.util.*;

class Test {
    void aaa(Test p) { return 1; }
    void test() {
        Comparator<Test> r2 = Test::test;
    }
}
