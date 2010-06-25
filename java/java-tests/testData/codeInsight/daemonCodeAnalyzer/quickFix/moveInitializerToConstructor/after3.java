// "Move initializer to constructor" "true"
public class X {
    <caret>final int fi;

    public X(String s) {
        super();
        fi = 0;
        int k = fi;
        k++;
    }
    public X() {
        this(0);
    }
    public X(int i) {
        // f
        int g = 0;
        fi = 0;
        int f = fi;
        g += f;
    }

}
