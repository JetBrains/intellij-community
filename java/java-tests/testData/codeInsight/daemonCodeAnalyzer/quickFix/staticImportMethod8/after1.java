// "Import static method 'java.util.Collections.emptyList()'" "true-preview"
import java.util.List;

import static java.util.Collections.emptyList;

public class X {
    List<String> get() {
        return emptyList();
    }
}