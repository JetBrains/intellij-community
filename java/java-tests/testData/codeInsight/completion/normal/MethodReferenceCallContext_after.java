import java.util.*;

class Test {
    void aaa(Test p) { return 1; }
    void test() {
        c(Test::c);
    }
    void c(Comparator<Test> c){}
}
