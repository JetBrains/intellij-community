// "Import static method 'java.util.Collections.emptyList()'" "true-preview"
import java.util.List;
public class X {
    List<String> get() {
        return <caret>emptyList();
    }
}