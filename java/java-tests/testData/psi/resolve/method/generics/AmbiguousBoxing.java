public class Ambiguous {
    void f(Object o1, Object o2) {}
    void f(int o1, int o2) {}

    void g(Integer i)
    {
        <ref>f(1, i);
    }
}