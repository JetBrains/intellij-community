public class Test {
    void anotherMethod(String s);
    String field;
    /**
     */
    void <caret>method() {
        anotherMethod(field);
    }
}