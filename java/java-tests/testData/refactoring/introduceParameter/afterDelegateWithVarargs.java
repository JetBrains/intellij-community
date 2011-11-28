public class Test {
    void m(int... ps) {
        m("asd", ps);
    }

    void m(final String anObject, int... ps) {
                System.out.println(anObject);
        }
}