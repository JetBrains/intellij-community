package privatebound;

import java.util.Collections;
import java.util.List;

public class PrivateGenericTypeParameter {
    public static void main(String[] args) {
        new PrivateGenericTypeParameter().foo(Collections.singletonList(new PrivateValue()));
    }

    private <T extends List<PrivateValue>> void foo(T value) {
        <caret>int x = 0;
    }

    private static class PrivateValue {
    }
}
