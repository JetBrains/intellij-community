// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

interface Role {}
class FooRole {
  public Role[] getRoles(Predicate<Object> p, List<Role> roles) {
      List<Role> list = new ArrayList<>();
      for (Role role : roles) {
          if (p.test(role)) {
              list.add(role);
          }
      }
      return list.toArray(new Role[0]);
  }
}