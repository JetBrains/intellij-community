package privatebound;

import java.util.Collections;
import java.util.List;

public class PrivateWildcardTypeParameter {
    public static void main(String[] args) {
        new PrivateWildcardTypeParameter().foo(Collections.singletonList(new PrivateValue()));
    }

    private <T extends List<? extends PrivateValue>> void foo(T value) {
        <caret>int x = 0;
    }

    private static class PrivateValue {
    }
}
