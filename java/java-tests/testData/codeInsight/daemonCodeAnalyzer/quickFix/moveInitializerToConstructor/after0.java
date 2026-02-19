// "Move initializer to constructor" "true-preview"
public class X {
    <caret>int i;

    public X() {
        i = 7;
    }
}
