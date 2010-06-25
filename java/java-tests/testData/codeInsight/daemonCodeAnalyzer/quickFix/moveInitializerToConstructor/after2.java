// "Move initializer to constructor" "true"
public class X {
    final int s=0;
    <caret>final int i;

    public X(String s) {
        super();
        i = this.s;
    }
    public X() {
        this(0);
    }
    public X(int i) {
        this.i = s;
    }

}
