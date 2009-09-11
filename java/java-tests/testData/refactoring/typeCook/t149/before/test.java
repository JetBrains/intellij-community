import java.util.*;

class Test
{
    private void testForEach(List values) {

        for (Object value : values) {

        }
    }

    void foo() {
        List l = new ArrayList();
        l.add(0);
        l.add(1);
        testForEach(l);
    }
}