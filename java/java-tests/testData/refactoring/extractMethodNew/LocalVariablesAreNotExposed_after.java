import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Test {

    public Test(Object o1, Object o2) {
    }

    private Object test(List<String> list) {
        Test x = newMethod(list);
        if (x != null) return x;
        return null;
    }

    private @Nullable Test newMethod(List<String> list) {
        for (String some : list) {
            String x = "x";
            String y = "y";
            if (some.isEmpty()) return new Test(x, y);
        }
        return null;
    }

}