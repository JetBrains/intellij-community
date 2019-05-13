import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

class App {

  {
    final User user = new User("test", Stream.of("TEST").collect(toSet()));
    Optional.of(user).map(u -> new User(u.getName(),
                                        u.getAttributes().stream().filter(a -> !a.equals("TEST")).collect(toSet())));
  }

  private static final class User {
    User(final String name, final Set<String> attributes) {
    }

    public String getName() {
      return null;
    }

    public Set<String> getAttributes() {
      return null;
    }
  }
}
