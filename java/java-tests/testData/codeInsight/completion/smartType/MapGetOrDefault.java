import java.util.concurrent.TimeUnit;
import java.util.*;

public class SomeClass {
    String com(Map<TimeUnit, String> map) {
        return map.getOrDefault(<caret>);
    }
}