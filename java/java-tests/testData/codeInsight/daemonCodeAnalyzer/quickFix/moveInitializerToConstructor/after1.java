// "Move initializer to constructor" "true"
public class X {
    final int i<caret>;

    public X(String s) {
        i = 0;
    }
    public X() {
        this(0);
    }
    public X(int i) {
        this.i = 0;
    }

}
