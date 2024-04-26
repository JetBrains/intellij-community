import org.junit.*;

import static org.junit.Assert.assertTrue;

public class BoxedComparisonToEquals {
    void test(Integer a, int b) {
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(a == b);
    }
}