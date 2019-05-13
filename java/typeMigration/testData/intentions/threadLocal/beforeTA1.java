// "Convert to ThreadLocal" "true"
import java.lang.annotation.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { int value(); }

class Test {
    @TA(42) int <caret>field = 0;
}