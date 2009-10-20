enum Setup {
  NO_CONNECTION,
  CONNECTION
}

@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Test {
  Setup setup();
}

public class Client {
  @Test(setup = Setup.CONNECTION<caret>)
  public void run() {
  }
}