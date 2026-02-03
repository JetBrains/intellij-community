import java.util.ArrayList;

class B {
    I getA() { return new A(); };
    void test(I a) {
    }

    int method(ArrayList list) {
        I a = getA();

        test(a);
   }
}