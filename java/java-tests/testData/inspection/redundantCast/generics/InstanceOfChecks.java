import java.util.ArrayList;
import java.util.List;

class RedundantCast {
    boolean redundantCasts(Object o) {
        int p = 0;
        if ((Number)p instanceof Integer) {}
        if ((Number)(<warning descr="Casting 'p' to 'Object' is redundant">Object</warning>)p instanceof Integer) {}
        return (<warning descr="Casting 'o' to 'List' is redundant">List</warning>)o instanceof ArrayList;
    }
}
