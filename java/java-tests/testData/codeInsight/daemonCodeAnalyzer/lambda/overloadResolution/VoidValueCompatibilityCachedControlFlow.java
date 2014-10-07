
import java.io.IOException;

class Test {
  {
    queryForObject(
      (rs) -> {
        try {
          return readValue(rs);
        } catch (IOException e) {
          return new UserOptions();
        }
      }
    );
  }

  UserOptions readValue(String content) throws IOException {
    System.out.println(content);
    return null;
  }

  UserOptions readValue(Integer i) throws IOException {
    System.out.println(i);
    return null;
  }

  void queryForObject(Mapper rowMapper) {
    System.out.println(rowMapper);
  }

  void queryForObject(String requiredType) {
    System.out.println(requiredType);
  }

  interface Mapper {
    UserOptions mapRow(String rs) throws IOException;
  }

  class UserOptions {}
  
}