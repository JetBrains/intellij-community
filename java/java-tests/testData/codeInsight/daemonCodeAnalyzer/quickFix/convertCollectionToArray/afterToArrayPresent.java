// "Apply conversion '.toArray(new Test.User[0])'" "true"

import java.util.List;

class Test {
  interface User {

  }

  interface Query {

    List<?> getResultList();
  }

  public User[] getAllUsers(Query readQuery) {
    List<?> result = readQuery.getResultList();
    return (result != null) ? result.toArray(new User[0]) : new User[0];
  }

  public static void main(String[] args) {
  }
}