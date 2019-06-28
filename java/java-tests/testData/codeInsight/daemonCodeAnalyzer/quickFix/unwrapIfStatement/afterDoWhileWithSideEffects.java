// "Unwrap 'do-while' statement (may change semantics)" "true"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        System.out.println("aaahh");
    }
}