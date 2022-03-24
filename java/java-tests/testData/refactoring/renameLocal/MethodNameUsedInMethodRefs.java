public class FooBar {
    
    static void f<caret>oo() {}


    public static void main(String[] args) throws Exception {
        Runnable runnable = FooBar::foo;
    }
}

class FooBarBaz {
     public static void main(String[] args) throws Exception {
        Runnable runnable = FooBar::foo;
    }
}
