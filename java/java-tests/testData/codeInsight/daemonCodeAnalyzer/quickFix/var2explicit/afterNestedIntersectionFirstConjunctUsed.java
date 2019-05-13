import java.io.Serializable;
import java.util.List;

// "Replace 'var' with explicit type" "true"
class Main {
    {
        List<Serializable> b = java.util.Arrays.asList("", 2);
        final Object o = b.get(0);
    }
}