// "Replace method call on lambda with lambda body" "true-preview"

import java.util.function.Supplier;

abstract class TrivialUsageInline {

    public <T> T evaluateUnderLock(Supplier<T> supplier) {
        if (true) {
            return supplier.get();
        }
        else {
            return null;
        }

    }
}
