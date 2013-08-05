import java.util.ArrayList;
import java.util.List;

public class RedundantCast {
    boolean redundantCasts(Object o) {
        int p = 0;
        if ((Number)p instanceof Integer) {}
        return (List)o instanceof ArrayList;
    }
}
