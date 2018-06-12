class Foo {
    interface Fn {
        void doS<caret>mth(String a);
    }
    
    Fn fn = a -> System.out.println(a);
}
