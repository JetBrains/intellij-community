// "Replace Stream API chain with loop" "false"

import java.util.List;
import java.util.function.Predicate;

interface Role {}
class FooRole {
  public Role[] getRoles(Predicate p, List<Role> roles) {
    return roles.stream().filter(p).toArr<caret>ay (Role[]::new);
  }
}