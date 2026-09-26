import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Nullability {

  public static void main(String[] args) {
    var nullability = new Nullability();
    nullability.useValue();
    nullability.tryToUseValue.run();
  }

  @Nullable
  private String value = null;

  Runnable tryToUseValue = () -> {
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  };

  public void useValue() {
    System.out.println(value.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }
}