// "Move initializer to constructor" "true"
public class X {
    <caret>final int fi=0;

    public X(String s) {
        super();
        int k = fi;
        k++;
    }
    public X() {
        this(0);
    }
    public X(int i) {
        // f
        int g = 0;
        int f = fi;
        g += f;
    }

}
