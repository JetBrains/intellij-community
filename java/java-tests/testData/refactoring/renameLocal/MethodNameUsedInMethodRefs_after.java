public class FooBar {
    
    static void bar1() {}


    public static void main(String[] args) throws Exception {
        Runnable runnable = FooBar::bar1;
    }
}

class FooBarBaz {
     public static void main(String[] args) throws Exception {
        Runnable runnable = FooBar::bar1;
    }
}
