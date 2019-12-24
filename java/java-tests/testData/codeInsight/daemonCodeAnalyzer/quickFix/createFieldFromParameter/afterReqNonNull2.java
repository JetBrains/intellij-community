// "Create field for parameter 'val'" "true"

import java.util.Objects;

class Test{
    String s;
    private String myVal;

    public void Test(String val, String message) {
        myVal = val;
        s = Objects.requireNonNull(message, val);
    }
}

