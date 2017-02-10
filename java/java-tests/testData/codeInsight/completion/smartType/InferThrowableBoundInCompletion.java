import java.io.IOException;
import java.util.function.Function;

class LocalDateTime {}
class Launcher {
  public static final Function<LocalDateTime, IOException> TO_STRING = (message) -> new IOException(message.toString());

  public static void main(String[] args) throws Throwable{
    throwException(new LocalDateTime(), TO<caret>);
  }

  public static <Ex extends Throwable> void throwException(LocalDateTime str, Function<LocalDateTime, Ex> exceptionBuilder) throws Ex {
    throw exceptionBuilder.apply(str);
  }
}