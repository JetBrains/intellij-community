import java.util.*;

class Test {
    void test() {
        Comparator<Test> r2 = Test::yyy;
        Comparator1<Test> c1 = <error descr="Non-static method cannot be referenced from a static context">Test::yyy</error>;
        Comparator1<Test> c2 = <error descr="Non-static method cannot be referenced from a static context">Test::xxx</error>;
    }
    int yyy(Test... p) { return 1; }
    int xxx(Test t) {return 42;}
}

interface Comparator1<T> {
    int compare(T... o1);
}


class Test1 {
    void test() {
        Bar2 bar2 = Test1::yyy;
    }

    void yyy(Test1... p) {}

    interface Bar2 {
      public void xxx(Test1 p, Test1... ps);
    }
}
