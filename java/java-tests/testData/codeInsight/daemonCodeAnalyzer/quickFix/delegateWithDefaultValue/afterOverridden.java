// "Generate overloaded method with default parameter values" "true"
class Test {
    void method(int x, int y) {}
    
    static class SubTest extends Test {
        void method() {
            method(0, 0);
        }

        @Override
        void method(int x, int y) {}
    }
}