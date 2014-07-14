public class Test {
    String[] f = new String[0];
    void foo() {
      bar(1, f);
    }

    void bar(int i, String[] g){}
}
