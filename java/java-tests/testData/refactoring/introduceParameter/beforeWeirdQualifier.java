public class Test {
    int method(int i) {
        return 0;
    }

    int m(int i, int j) {
        return i + <selection>method(j)</selection>;
    }
}

class X {
   public int n(int a) {
        return (new Test()).m(a, a * 2);
   }
}