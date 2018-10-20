import java.util.ArrayList;
import java.util.List;

class RedundantCast {
    boolean redundantCasts(Object o) {
        int p = 0;
        if ((Number)p instanceof Integer) {}
        return (<warning descr="Casting 'o' to 'List' is redundant">List</warning>)o instanceof ArrayList;
    }
}
