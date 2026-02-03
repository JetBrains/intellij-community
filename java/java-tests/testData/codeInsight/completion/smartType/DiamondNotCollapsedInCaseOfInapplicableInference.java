import java.util.ArrayList;

public class CompletionWithPassToMethod {

    public static void main(String[] args) {
        foo(new ArrayL<caret>);
    }


    static void foo(ArrayList<String> ll) {
    }
}
