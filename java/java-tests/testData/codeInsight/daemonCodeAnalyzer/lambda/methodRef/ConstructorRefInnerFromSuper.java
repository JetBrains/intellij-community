class MyTest {

    static class Super {
        class Inner {
            Inner(int i){}
        }
    }

    static class Child extends Super {

        interface I { 
          Inner m(Child child, int i); 
        }

        void test() {
            <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest.Child.I'">I var = Child.Inner::new;</error>
        }
    }
}
