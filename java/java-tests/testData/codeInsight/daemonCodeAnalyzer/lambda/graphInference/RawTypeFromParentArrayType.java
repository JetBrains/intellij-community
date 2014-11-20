import java.util.*;

class Test {
  protected Class[] getAllInterfaces(final Set<Class> interfaces, final Class[] classes) {
    return interfaces.toArray(classes);
  }
  public static <T extends Collection<String>> T get(T out) {
    return out;
  }

  public static void main(String[] args) {
    Set<String> set = get(new HashSet());
  }
}