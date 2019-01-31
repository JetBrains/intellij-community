// "Surround with try/catch" "true"
class C {
    native boolean foo() throws Exception;
    
    void test() {
        if(foo() && f<caret>oo())
    }
    
}