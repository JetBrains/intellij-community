import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
    Object test(boolean c, @NotNull Set<String> set){
        Collection<String> x = newMethod(c, set);
        if (x != null) return x;
        return null;
    }

    @Nullable
    private Collection<String> newMethod(boolean c, @NotNull Set<String> set) {
        if (c) {
            return new ArrayList<String>();
        }
        if (!c) {
            return set;
        }
        return null;
    }
}