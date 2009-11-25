public class TestClass {
    void x() {
        new Exception() {
            int doSomething() { return 1; }
            void a() {
                int j = doSomething();
            }
            void b() {
                doSomething();
            }
        };
    }
}