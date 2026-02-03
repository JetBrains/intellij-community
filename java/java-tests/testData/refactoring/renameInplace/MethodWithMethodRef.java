class MyTest {
    
    static class Foo {
    }
    
    interface I {
        Foo m();
    }
    static Foo f<caret>oo() { return null; }

    public static void main(String[] args) {
        I i = MyTest::foo;
    }
}