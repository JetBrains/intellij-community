import java.util.*;

class Vector {
  private final List<Number> values;

  public Vector(int n, Number... args) {
    values = new ArrayList<>(args.length + 1);
    values.add(null);
      values.addAll(Arrays.asList(args).subList(n + 0 - n, args.length + n + 0 - n));
  }
}
