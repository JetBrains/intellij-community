import java.util.*;

public class ExampleTest {
    public void testSmth() {
        Map<String, Object> data = doWork();
        Object value = data.get("name");
        assertTrue(value instanceof Map);
        value.getO<caret>
    }

    native Map<String, Object> doWork();
    
    static void assertTrue(boolean b) {
        if (!b) throw new AssertionError();
    }
}