class MyTest {

   static void m(int i) {//here 
   }
   static void m(Integer i) {}

   interface I {
       void m(int x);
   }

   static void call(I s) { s.m(42); }

   public static void main(String[] args) {
       I s = MyTest::m;
       s.m(42);
       call(MyTest::m);
   }
}

class MyTest1 {

    static void m(Integer i) { }

    interface I1 {
        void m(int x);
    }

    interface I2 {
        void m(Integer x);
    }

    static void call(int i, I1 s) {
      
    }
    static void call(int i, I2 s) {
      //here
    }

    public static void main(String[] args) {
        call(1, MyTest1::m);
    }
}

class MyTest2 {

    static void m(Integer i) { }

    interface I {
        void m(int x);
    }

    static void call(int i, I s) {   }
    static void call(Integer i, I s) {   }

    static void test() {
        call<error descr="Cannot resolve method 'call(int, <method reference>)'">(1, MyTest2::m)</error>; //ambiguous
    }
}
