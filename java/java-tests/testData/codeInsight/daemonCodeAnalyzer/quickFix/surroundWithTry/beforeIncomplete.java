// "Surround with try/catch" "true-preview"
class C {
    native boolean foo() throws Exception;
    
    void test() {
        if(foo() && f<caret>oo())
    }
    
}