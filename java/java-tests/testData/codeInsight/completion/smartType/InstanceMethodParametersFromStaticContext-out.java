public class MyFirstTestClassBoo {

    static void foo(int a) {}

    void foo(String s) {}

    static {
        int xxy;
        String xxz;
        foo(xxy);<caret>
    }

}