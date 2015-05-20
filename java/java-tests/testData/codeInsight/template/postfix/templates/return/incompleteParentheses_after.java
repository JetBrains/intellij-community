public class Foo {
    String m() {
        (methodCall("").return  <caret>
    }

    String methodCall(String s) {
        return null;
    }
}