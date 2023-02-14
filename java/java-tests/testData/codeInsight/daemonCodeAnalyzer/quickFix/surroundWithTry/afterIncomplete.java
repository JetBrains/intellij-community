// "Surround with try/catch" "true-preview"
class C {
    native boolean foo() throws Exception;
    
    void test() {
        try {
            if(foo() && foo())
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
}