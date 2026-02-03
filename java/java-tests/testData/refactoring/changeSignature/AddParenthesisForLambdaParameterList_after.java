class Foo {
    interface Fn {
        void doSmth(int a, int b);
    }
    
    Fn fn = (a, b) -> System.out.println(a);
}
