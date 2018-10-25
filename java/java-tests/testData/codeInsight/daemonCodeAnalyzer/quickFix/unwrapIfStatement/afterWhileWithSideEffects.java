// "Remove 'while' statement extracting side effects" "true"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        if (obj != null) {
            test(obj);
        }
    }
}