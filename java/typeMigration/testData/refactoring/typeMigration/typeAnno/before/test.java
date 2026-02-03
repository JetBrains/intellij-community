import java.lang.annotation.*;
import java.util.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { }

class Test {
    ArrayList<@TA Integer> list = new ArrayList<>(2);
}