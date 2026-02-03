import java.util.*;

class Test {
    void aaa(Test p) { return 1; }
    void test() {
        c(Test::<caret>);
    }
    void c(Comparator<Test> c){}
}
