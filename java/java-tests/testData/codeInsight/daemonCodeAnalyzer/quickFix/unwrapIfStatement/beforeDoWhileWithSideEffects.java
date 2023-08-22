// "Unwrap 'do-while' statement (may change semantics)" "true-preview"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        do {
            System.out.println("aaahh");
        }
        while(obj <caret>!= null && test(obj) && obj == null);
    }
}