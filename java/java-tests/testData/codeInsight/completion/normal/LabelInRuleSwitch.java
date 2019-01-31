public class ConstConfig {
    enum X {FOO, BAR, BAZ}
    
    void test(X x) {
        switch (x) {
            case BAR -> {}
            case BAZ -> {}
            case FO<caret>
        }
    }
}