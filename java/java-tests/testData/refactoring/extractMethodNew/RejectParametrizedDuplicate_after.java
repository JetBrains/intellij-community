import java.util.Map;

class C {
    void foo(Map<String, Integer> m) {
        newMethod(m);
        m.put("b", 2);
    }

    private void newMethod(Map<String, Integer> m) {
        m.put("a", 1);
    }
}