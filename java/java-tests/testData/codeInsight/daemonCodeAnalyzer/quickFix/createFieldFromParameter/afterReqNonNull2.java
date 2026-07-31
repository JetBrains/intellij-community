// "Create field for parameter 'val'" "true-preview"

import java.util.Objects;

class Test{
    private String myVal;
    String s;
    
    public void Test(String val, String message) {
        myVal = val;
        s = Objects.requireNonNull(message, val);
    }
}

