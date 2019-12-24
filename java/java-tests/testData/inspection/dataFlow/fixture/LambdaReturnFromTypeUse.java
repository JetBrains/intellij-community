import typeUse.NotNull;
import java.util.function.Function;
class App {
  private static Function<@NotNull Integer, @NotNull Integer> impossibleToReturnNull() {
    return r -> <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
  }
  public static void main(String[] args) {
    final Function<@NotNull Integer, @NotNull Integer> functionNotNull = impossibleToReturnNull();
    final Integer nonNullResult = functionNotNull.apply(5);
    if (<warning descr="Condition 'nonNullResult == null' is always 'false'">nonNullResult == null</warning>) System.out.println("NULL");
    else System.out.println("NOT NULL");
  }
}