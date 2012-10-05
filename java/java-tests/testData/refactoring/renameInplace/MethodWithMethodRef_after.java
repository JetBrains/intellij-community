class MyTest {
    
    static class Foo {
    }
    
    interface I {
        Foo m();
    }
    static Foo bar() { return null; }

    public static void main(String[] args) {
        I i = MyTest::bar;
    }
}