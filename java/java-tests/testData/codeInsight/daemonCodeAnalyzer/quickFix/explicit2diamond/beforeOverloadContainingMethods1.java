// "Replace with <>" "false"

import java.util.Collections;
import java.util.List;

class Test {

    void testInbound() throws Exception {
        final Object[] with = (Object[])with(Collections.<Obj<caret>ect>emptyList());
    }

    <T> T with(List<T> l) {
        return null;
    }

    public boolean[] with(List<Boolean> matcher) {
        return null;
    }

    public double[] with(List<Double> matcher) {
        return null;
    }
}