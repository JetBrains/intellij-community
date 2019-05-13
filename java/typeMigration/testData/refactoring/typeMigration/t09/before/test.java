public class Test {
    Integer[] f = new Integer[0];
    void foo() {
      bar(1, f);
    }

    void bar(int i, Integer[] g){}
}
