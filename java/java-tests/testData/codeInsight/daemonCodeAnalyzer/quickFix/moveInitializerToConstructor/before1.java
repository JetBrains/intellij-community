// "Move initializer to constructor" "true-preview"
public class X {
    final int i = 0<caret>;

    public X(String s) {
    }
    public X() {
        this(0);
    }
    public X(int i) {
    }

}
