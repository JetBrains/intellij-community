import java.util.*;

class Test {
    int aaa(Test p) { return 1; }
    void test() {
        Comparator<Test> r2 = Test::aaa;<caret>
    }
}
