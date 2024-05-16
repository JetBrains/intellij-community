// "Generate overloaded method with default parameter values" "true"
class Test {
    void method(int x, int y) {}
    
    static class SubTest extends Test {
        @Override
        void meth<caret>od(int x, int y) {}
    }
}