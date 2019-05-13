import java.util.HashMap;
import java.util.Map;

interface A8<A1, A2> {
    A2 a(A1 a1);
}

interface B<B1, B2> {
    B2 b(B1 b1);
}

class Bug {
    <T1, T2> void bug(B<T1, T2> b) {
        Map<String, A8<?, T2>> m = new HashMap<>();
        // replace with lambda here
        m.put("", (A8<T1, T2>) t1 -> b.b(t1));
    }
} 