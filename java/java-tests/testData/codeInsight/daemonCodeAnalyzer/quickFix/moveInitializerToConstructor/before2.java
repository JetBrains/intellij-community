// "Move initializer to constructor" "true"
public class X {
    final int s=0;
    <caret>final int i=s;

    public X(String s) {
        super();
    }
    public X() {
        this(0);
    }
    public X(int i) {
    }

}
