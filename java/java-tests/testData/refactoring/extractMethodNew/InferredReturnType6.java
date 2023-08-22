import java.util.*;
import org.jetbrains.annotations.NotNull;

class Test {
    Object test(boolean c, @NotNull Set<String> set){
        <selection>if (c) {
            return new ArrayList<String>();
        }
        if (!c) {
            return set;
        }</selection>
        return null;
    }
}