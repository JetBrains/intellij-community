// "Import static method 'java.util.Collections.emptyList'" "true"
import java.util.List;
public class X {
    String emptyList = "";
    List<String> get() {
        return <caret>emptyList();
    }
}