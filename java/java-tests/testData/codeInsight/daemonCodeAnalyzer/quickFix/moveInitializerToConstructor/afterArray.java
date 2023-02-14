// "Move initializer to constructor" "true-preview"
public class X {
    <caret>String[] i;

    public X() {
        i = new String[]{"ss", "xx"};
    }
}
