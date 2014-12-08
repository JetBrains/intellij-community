public class Foo {
    void m() {
        methodCall(.if  <caret>
    }

    boolean methodCall(String s) {
        return null;
    }
}