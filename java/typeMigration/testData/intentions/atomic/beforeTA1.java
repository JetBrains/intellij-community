// "Convert to atomic" "true-preview"
import java.lang.annotation.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { int value(); }

class T {
    @TA(42) String <caret>v;
}