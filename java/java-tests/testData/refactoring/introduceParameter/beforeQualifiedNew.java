class Test {
    class A {
        class B {
        }
    }
 
    Object method() {
        return <selection>new A().new B()</selection>;
    }
}