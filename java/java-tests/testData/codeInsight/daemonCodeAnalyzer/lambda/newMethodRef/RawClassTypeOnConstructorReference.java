import java.util.*;
class Test {
  public Optional<Set<String>> foo(Optional<Set<String>> args) {
     return args.map(HashSet::new).map(Collections::unmodifiableSet);
  }
}
