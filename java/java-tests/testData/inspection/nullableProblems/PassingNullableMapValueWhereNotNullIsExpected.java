import typeUse.*;
import java.util.*;

class Test {

  public @NotNull Map<@NotNull String, @NotNull ArrayList<@NotNull String>> getDataBroken() {
    ArrayList<@Nullable String> arrayList = new ArrayList<>();
    arrayList.add(null);

    @NotNull Map<@NotNull String, @NotNull ArrayList<@Nullable String>> map = new HashMap<>();
    map.put("x", arrayList);
    return <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">map</warning>;
  }
}