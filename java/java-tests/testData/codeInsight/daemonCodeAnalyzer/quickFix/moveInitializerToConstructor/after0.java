// "Move initializer to constructor" "true"
public class X {
    <caret>int i;

    public X() {
        i = 7;
    }
}
