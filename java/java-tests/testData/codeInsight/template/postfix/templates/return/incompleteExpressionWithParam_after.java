public class Foo {
    String m() {
        methodCall("string".return  <caret>
    }

    String methodCall(String s) {
        return null;
    }
}