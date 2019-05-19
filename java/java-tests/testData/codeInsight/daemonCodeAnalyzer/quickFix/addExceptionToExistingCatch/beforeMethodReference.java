// "Add exception to existing catch clause" "false"
import java.util.*;

class MyTest {

    void demo(List<String> strings) {
        try {
            strings.forEach(this::f<caret>oo);
        }
        catch (Exception ex) {
            // handle
        }
    }

    private void foo(String s) throws Exception {
        throw new Exception();
    }
}