// "Convert to ThreadLocal" "true"
import java.lang.annotation.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { int value(); }

class Test {
    final ThreadLocal<@TA(42) Integer> field = ThreadLocal.withInitial(() -> 0);
}