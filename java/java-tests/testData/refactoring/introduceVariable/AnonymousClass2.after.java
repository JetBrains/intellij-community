public class TestClass {
    void x() {
        new Exception() {
            final int j = doSomething();

            int doSomething() { return 1; }
            void a() {
                j;
            }
            void b() {
                j;
            }
        };
    }
}