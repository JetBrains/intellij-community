// "Move initializer to constructor" "true-preview"
public class X {
    <caret>final int fi;

    public X(String s) {
        fi = 0;
    }

}
