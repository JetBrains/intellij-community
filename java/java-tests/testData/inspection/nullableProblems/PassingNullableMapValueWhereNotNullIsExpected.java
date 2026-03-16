import typeUse.*;
import java.util.*;

class Test {

  public @NotNull Map<@NotNull String, @NotNull ArrayList<@NotNull String>> getDataBroken() {
    ArrayList<@Nullable String> arrayList = new ArrayList<>();
    arrayList.add(null);

    @NotNull Map<@NotNull String, @NotNull ArrayList<@Nullable String>> map = new HashMap<>();
    map.put("x", arrayList);
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">map</warning>;
  }
}