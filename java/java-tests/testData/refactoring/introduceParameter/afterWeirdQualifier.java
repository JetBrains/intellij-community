public class Test {
    int method(int i) {
        return 0;
    }

    int m(int i, int j, int anObject) {
        return i + anObject;
    }
}

class X {
   public int n(int a) {
       final Test test = new Test();
       return test.m(a, a * 2, test.method(a * 2));
   }
}