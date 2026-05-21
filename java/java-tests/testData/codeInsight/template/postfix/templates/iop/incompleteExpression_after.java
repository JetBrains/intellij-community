public class Foo {
    void m() {
        methodCall(.iop <caret>
    }

    String methodCall(String s) {
        return null;
    }
}