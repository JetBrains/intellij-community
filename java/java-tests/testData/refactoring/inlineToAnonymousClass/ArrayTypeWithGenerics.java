import java.util.*;

class A {
    public void test() {
        Inner<String>[] b = new Inner<String>[1];
        b [0] = new Inner<String>();
    }

    private class <caret>Inner<T> implements Comparator<T> {
        public int compare(T t1, T t2) {
            return 0;
        }
    }
}