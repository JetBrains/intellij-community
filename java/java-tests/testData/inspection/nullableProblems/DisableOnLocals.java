import org.jspecify.annotations.Nullable;

import java.util.List;

class Demo {
  public void demo() {
    <warning descr="Nullability annotation is not applicable to local variables">@Nullable</warning> String str;

    <warning descr="Nullability annotation is not applicable to local variables">@Nullable</warning> List<@Nullable String> list;

    @Nullable Boolean[] array1 = new Boolean[]{true, false, null};
    Boolean <warning descr="Nullability annotation is not applicable to local variables">@Nullable</warning> [] array2 = null;
    Boolean <warning descr="Nullability annotation is not applicable to local variables">@Nullable</warning> [] [] array3 = null;
    Boolean [] @Nullable [] array4 = null;
  }
}