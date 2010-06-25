// "Move initializer to constructor" "true"
public class X {
    <caret>final int fi;

    public X(String s) {
        //To change body of created methods use File | Settings | File Templates.
        fi = 0;
    }

}
