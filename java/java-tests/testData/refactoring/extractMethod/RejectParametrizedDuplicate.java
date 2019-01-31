import java.util.Map;

class C {
    void foo(Map<String, Integer> m) {
        <selection>m.put("a", 1);</selection>
        m.put("b", 2);
    }
}