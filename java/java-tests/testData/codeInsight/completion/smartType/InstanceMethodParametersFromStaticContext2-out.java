public class MyFirstTestClassBoo {

    void foo(String s) {}

    static {
        String xxz;
        foo(xxz);<caret>
    }

}