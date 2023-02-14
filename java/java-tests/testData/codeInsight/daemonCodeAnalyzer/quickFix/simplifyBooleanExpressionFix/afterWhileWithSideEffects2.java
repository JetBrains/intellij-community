// "Simplify 'test(...) || obj == null' to true extracting side effects" "true-preview"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        while(obj != null) {
            test(obj);
            System.out.println("aaahh");
        }
    }
}