public class A {
    void f() {
        new My() {
            public void f() {
                int i = 9;
                int j = 9;
            }
        };
        new My() {
            public void f() {
                l();
                l();
            }
        };
    }

}