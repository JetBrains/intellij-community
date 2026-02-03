import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoxedComparisonToEquals {
    void test(Integer a, int b) {
        assertEquals((int) a, b);
    }
}