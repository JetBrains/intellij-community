// "Remove 'while' statement extracting side effects" "true-preview"
import org.jetbrains.annotations.Contract;

class X {
    @Contract("_ -> true")
    boolean test(Object obj) {
        return true;
    }

    void doSmth(Object obj) {
        while(obj <caret>!= null && test(obj) && obj == null) {
            System.out.println("aaahh");
        }
    }
}