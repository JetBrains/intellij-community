// "Wrap with unmodifiable list" "false"
import java.util.List;
import java.util.ArrayList;

class C {
    ArrayList<String> test() {
        ArrayList<String> result = new ArrayList<>();
        return <caret>result;
    }
}