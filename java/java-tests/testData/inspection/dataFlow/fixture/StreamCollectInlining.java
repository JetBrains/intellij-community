import foo.NotNull;
import foo.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class Clazz {
  public void passNull(Collection<?> c) {
    c.stream().collect(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, 
                       <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, 
                       <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
  
  public void doSomethingStupid(Collection<String> c) {
    String result = c.stream().collect(() -> "", String::concat, String::concat);
    if (<warning descr="Condition 'result.isEmpty()' is always 'true'">result.isEmpty()</warning>) {
    }
  }
  
  public Map<@NotNull String, String> multiGet(Collection<@Nullable String> keys) {
    if (keys == null || keys.isEmpty()) {
      return new HashMap<>();
    }

    return keys.stream()
      .filter(Objects::nonNull)
      .collect(HashMap::new, (m, v) -> m.put(v, get(v)), HashMap::putAll);
  }

  public Map<@NotNull String, String> multiGet2(Collection<@Nullable String> keys) {
    if (keys == null || keys.isEmpty()) {
      return new HashMap<>();
    }

    return keys.stream()
      .collect(HashMap::new, (m, v) -> m.put(<warning descr="Argument 'v' might be null">v</warning>, get(v)), HashMap::putAll);
  }

  private String get(String v) {
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }
}