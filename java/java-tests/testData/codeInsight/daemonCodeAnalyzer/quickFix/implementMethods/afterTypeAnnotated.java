// "Implement Methods" "true"
import java.lang.annotation.*;
import java.util.*;

@Target(ElementType.TYPE_USE)
@interface TA { int value() default 0; }

interface I {
    @TA List<@TA String> i(@TA int p1, @TA(1) int @TA(2) [] p2 @TA(3) []) throws @TA IllegalArgumentException;
}

class C implements I {
    @Override
    public @TA List<@TA String> i(@TA int p1, @TA(1) int @TA(2) [] @TA(3) [] p2) throws @TA IllegalArgumentException {
        return null;
    }
}
