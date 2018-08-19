import static java.lang.Math.abs;
import static java.lang.Math.sin;

class ImportTest {
    {
        abs(-0.5);
        sin(0.5);
        Math.ma<caret>x(1, 2);
        Math.min(1, 2);
    }
}