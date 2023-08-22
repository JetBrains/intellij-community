import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        <caret>for (int i = 1; i <= args.length; ++i) {
            values.add(args[i - 1]);
        }
    }
}
