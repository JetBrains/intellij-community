
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ParameterizedTestsDemo {
  enum E {
    A, B;
  }

  @ParameterizedTest
  @ValueSource(strings = {"A"})
  void testStrToEnum(E e) { }

  @ParameterizedTest
  @ValueSource(strings = {"1"})
  void testStrToPrimitive(int i) { }
  
  @ParameterizedTest
  @ValueSource(strings = "title")
  void testConstructor(Book book) { }

  static  class Book {
    public Book(String title) {}
  }

  @ParameterizedTest
  @ValueSource(strings = "title")
  void testFactoryMethod(BookWithFactory book) { }

  static  class BookWithFactory {
    public static BookWithFactory create(String title) { return null;}
  }
}
