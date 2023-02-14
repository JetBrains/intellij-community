import org.jetbrains.annotations.*;
import java.util.List;

public class NullableCallWithPrecalculatedValueAndSpecialField {
  @NotNull List<String> test() {
    return <warning descr="Expression 'getNulableValue()' might evaluate to null but is returned by the method declared as @NotNull">getNulableValue()</warning>;
  }
  
  native @Nullable List<String> getNulableValue();
}