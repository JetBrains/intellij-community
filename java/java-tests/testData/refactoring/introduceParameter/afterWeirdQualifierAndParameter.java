public class Test {    
    int method(int i) {
        return 0;
    }

    int m(int i, int j, Test t, int anObject) {
        return i + anObject;
    }
}

class X {
   public int n(int a) {
       final Test t = new Test();
       return (new Test()).m(a, a * 2, t, t.method(a * 2));
   }
}