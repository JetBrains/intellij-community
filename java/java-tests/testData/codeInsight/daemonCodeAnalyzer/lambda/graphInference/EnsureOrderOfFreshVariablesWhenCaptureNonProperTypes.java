
import java.util.Map;

class MyTest {

    private static <T> Map<? super T, ? super String> foo() {
        return null;
    }

    {
        Map<? super Integer, ? super String> action2 = foo();
    }
}
