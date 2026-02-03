import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestInspection {

  private static class StringWrapper {
    private String inner = null;

    @Nullable
    public String getString() {
      return inner;
    }
  }

  @NotNull
  private final StringWrapper wrapper;

  @Nullable
  private String string = null;

  public TestInspection() {
    wrapper = new StringWrapper();
  }

  public void doTest() {
    if (this.wrapper.getString() != null) {
      doSomething(this.wrapper.getString());
    }
    if (this.wrapper.getString() != null) {
      doSomething(wrapper.getString());
    }
    if (wrapper.getString() != null) {
      doSomething(this.wrapper.getString());
    }
    if (wrapper.getString() != null) {
      doSomething(wrapper.getString());
    }
    if (this.string != null) {
      doSomething(this.string);
    }
  }

  private void doSomething(@NotNull String s) {
    //...
  }
}

