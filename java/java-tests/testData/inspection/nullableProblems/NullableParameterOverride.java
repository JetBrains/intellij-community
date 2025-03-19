import org.jetbrains.annotations.*;

interface MyService {
  void doFirst(@NotNull String name);
  void doSecond();
}

class MyServiceMock implements MyService {
  public void doFirst(@Nullable String name) {
    System.out.println(name);
  }

  public void doSecond() {
    doFirst(null);
  }
}