// "Simplify 'test(...) || obj == null' to true extracting side effects" "true"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        while(obj != null && (test(obj) <caret>|| obj == null)) {
            System.out.println("aaahh");
        }
    }
}