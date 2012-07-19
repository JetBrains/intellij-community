interface I {
  int m(int i);
}

public class XXX {

    static void foo() {
        int l = 0;
        int j = 0;
        j = 2;
        final int L = 0;
        I i = (int h) -> { int k = 0; return h + <error descr="Variable used in lambda expression should be effectively final">j</error> + l + L; };
    }

    void bar() {
        int l = 0;
        int j = 0;
        j = 2;
        final int L = 0;
        I i = (int h) -> { int k = 0; return h + k + <error descr="Variable used in lambda expression should be effectively final">j</error> + l + L; };
    }
}
