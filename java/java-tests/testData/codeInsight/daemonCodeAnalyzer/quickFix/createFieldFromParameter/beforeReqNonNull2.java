// "Create field for parameter 'val'" "true-preview"

import java.util.Objects;

class Test{
    String s;
    
    public void Test(String <caret>val, String message) {
        s = Objects.requireNonNull(message, val);
    }
}

