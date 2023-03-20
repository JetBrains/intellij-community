// "Replace Stream API chain with loop" "true-preview"

import java.util.List;
import java.util.function.Predicate;

interface Role {}
class FooRole {
  public Role[] getRoles(Predicate<Object> p, List<Role> roles) {
    return roles.stream().filter(p).toArr<caret>ay (Role[]::new);
  }
}