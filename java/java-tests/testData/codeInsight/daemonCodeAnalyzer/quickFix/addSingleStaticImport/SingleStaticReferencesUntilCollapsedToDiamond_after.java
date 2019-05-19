import static java.lang.Math.*;

class ImportTest {
    {
        abs(-0.5);
        sin(0.5);
        ma<caret>x(1, 2);
        Math.min(1, 2);
    }
}