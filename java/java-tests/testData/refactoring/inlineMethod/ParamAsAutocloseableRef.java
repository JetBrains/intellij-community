class X {
    void use() {
        AutoCloseable r = () -> {};
        <caret>test(r);
    }
    
    void test(AutoCloseable ref) {
        try(ref) {
            System.out.println(ref);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}