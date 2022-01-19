// "Surround with try/catch" "true"
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