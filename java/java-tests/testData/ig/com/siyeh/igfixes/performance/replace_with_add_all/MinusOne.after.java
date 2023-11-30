import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        values.addAll(Arrays.asList(args).subList(2, args.length + 1));
    }
}