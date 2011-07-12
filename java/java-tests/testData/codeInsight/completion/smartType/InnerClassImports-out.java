import java.awt.geom.Point2D;

class UTest {

    void foo() {
        Point2D.Double d = new Point2D.Double();<caret>
    }

    void bar() {
        Double d;
    }
}