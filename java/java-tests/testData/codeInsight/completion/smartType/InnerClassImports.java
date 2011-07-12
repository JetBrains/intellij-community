import java.awt.geom.Point2D;

class UTest {

    void foo() {
        Point2D.Double d = new <caret>
    }

    void bar() {
        Double d;
    }
}