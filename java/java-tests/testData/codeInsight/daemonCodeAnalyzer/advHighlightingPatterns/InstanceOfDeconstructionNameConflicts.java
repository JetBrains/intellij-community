public class InstanceOfDeconstructionNameConflicts {

  public interface Container {
  }

  public record ValueContainerA(Object value) implements Container {
  }

  public record ValueContainerB(Object value) implements Container {
  }

  public record ValueContainerC(Object value, Object value2) implements Container {
  }

  public static boolean firstDeconstruction(Container x) {
    return (x instanceof ValueContainerA(Object value) && value.hashCode() == 0) ||
           (x instanceof ValueContainerB(Object <error descr="Variable 'value' is already defined in the scope">value</error>) && value.hashCode() == 0);
  }

  public static boolean doubleDeconstruction(Container x) {
    return (x instanceof ValueContainerA(Object value) && value.hashCode() == 0) ||
           (x instanceof ValueContainerC(Object <error descr="Variable 'value1' is already defined in the scope">value1</error>, Object <error descr="Variable 'value1' is already defined in the scope">value1</error>));
  }

  public static boolean nestedDeconstruction(Container x) {
    return (x instanceof ValueContainerA(ValueContainerA(Object value)) && value.hashCode() == 0) ||
           (x instanceof ValueContainerB(ValueContainerB(Object <error descr="Variable 'value' is already defined in the scope">value</error>)) && value.hashCode() == 0);
  }

  public static boolean nestedDeconstructionDouble(Container x) {
    return (x instanceof ValueContainerA(ValueContainerA(Object value2)) && value2.hashCode() == 0) ||
           (x instanceof ValueContainerC(ValueContainerB(Object <error descr="Variable 'value' is already defined in the scope">value</error>), ValueContainerB(Object <error descr="Variable 'value' is already defined in the scope">value</error>)));
  }
}
