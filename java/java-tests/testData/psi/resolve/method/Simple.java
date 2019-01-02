public class Simple {
    public void method(String s) {
    }

    static {
        Simple a = new Simple();
        a.<caret>method("blah");
    }
}