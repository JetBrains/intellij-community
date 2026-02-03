import java.util.List;

class Test {
    void foo() {
        List<List<?>> a = bar();
    }
    
    <T> List<List<T>> bar() {
        return null;
    }
}