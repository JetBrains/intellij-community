import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

class UTest {

    void foo() {
        Point2D.Double d = new Double();<caret>
    }

    void bar() {
        Double d;
    }
}