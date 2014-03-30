import java.util.*;

class Specificss {
  {
    Set<String> JAR_EXTENSIONS  = newTroveSet(new ArrayList<String>(), "jar", "zip", "swc", "ane");
  }


  public static <T> HashSet<T> newTroveSet(T... elements) {
    return newTroveSet(Arrays.asList(elements));
  }

  public static <T> HashSet<T> newTroveSet(List<T> strategy, T... elements) {
    return new HashSet<T>();
  }

}