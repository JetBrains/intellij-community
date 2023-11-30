import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        Collections.addAll(values, args);
    }
}
