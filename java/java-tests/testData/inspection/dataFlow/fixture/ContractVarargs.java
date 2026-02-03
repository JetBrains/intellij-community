import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.RuntimeException;

class Contracts {

  public void simpleFail(@Nullable String message) {
    notBlank(message);
    log(message);
  }

  @Contract("_->fail")
  private void notBlank(@Nullable Object message) {
    throw new RuntimeException();
  }

  @Contract("_,_,_->fail")
  private void notBlank(@Nullable Object o, String message, Object... args) {
    throw new RuntimeException();
  }

  public void varargFail(@Nullable String message) {
    notBlank(message, "Message should not be blank");
    log(message);
  }

  public void vararg1(@Nullable String message) {
    notBlank(message, "Message should not be blank", new Object());
    log(message);
  }
  public void vararg2(@Nullable String message) {
    notBlank(message, "Message should not be blank", new Object(), new Object());
    log(message);
  }
  public void vararg3(@Nullable String message) {
    notBlank(message, "Message should not be blank", new Object(), new Object(), new Object(), new Object(), new Object());
    log(message);
  }

  public void log(@NotNull String message) {
    System.out.println(message);
  }
}