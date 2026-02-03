public class TestClass {
    void x() {
        new Exception() {
            int doSomething() { return 1; }
            void a() {
                <selection>doSomething()</selection>;
            }
            void b() {
                doSomething();
            }
        };
    }
}