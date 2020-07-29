import java.util.*;

public class ExampleTest {
    public void testSmth() {
        Map<String, Object> data = doWork();
        Object value = data.get("name");
        assertTrue(value instanceof Map);
        ((Map<?, ?>) value).getOrDefault()
    }

    native Map<String, Object> doWork();
    
    static void assertTrue(boolean b) {
        if (!b) throw new AssertionError();
    }
}