import org.jspecify.annotations.Nullable;

import java.util.List;

class Demo {
  public void demo() {
    @Nullable String str;

    @Nullable List<@Nullable String> list;

    @Nullable Boolean[] array1 = new Boolean[]{true, false, null};
    Boolean @Nullable [] array2 = null;
    Boolean @Nullable [] [] array3 = null;
    Boolean [] @Nullable [] array4 = null;
  }
}