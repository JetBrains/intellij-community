import java.util.*;
class Test {
    List<String> f;
    String[] get() {
        return f.toArray(new String[f.size()]);
    }
}
