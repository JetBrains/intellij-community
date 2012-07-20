interface I {
  int m(int i);
}
interface J {
  int m();
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
    
     void foo(J i) { }
 
     void m1(int x) {
         int y = 1;
         foo(() -> x+y);
     }
 
     void m2(int x) {
         int y;
         y = 1;
         foo(() -> x+y);
     }
 
     void m3(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         foo(() -> x+<error descr="Variable used in lambda expression should be effectively final">y</error>);
     }
 
     void m4(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         else y = 2;
         foo(() -> x+y);
     }
 
     void m5(int x, boolean cond) {
         int y;
         if (cond) y = 1;
         y = 2;
         foo(() -> x+<error descr="Variable used in lambda expression should be effectively final">y</error>);
     }
 
     void m6(int x) {
         foo(() -> <error descr="Variable used in lambda expression should be effectively final">x</error>+1);
       x++;
     }
 
     void m7(int x) {
         foo(() -> <error descr="Variable used in lambda expression should be effectively final">x</error>=1);
     }
 
     void m8() {
         int y;
         foo(() -> <error descr="Variable used in lambda expression should be effectively final">y</error>=1);
     }
}
