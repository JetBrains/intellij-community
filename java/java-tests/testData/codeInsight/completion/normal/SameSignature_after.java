import java.io.File;

class Zoo {
  private Zoo delegate;
  @Override
  public void foo(String s, File file) {
    delegate.foo(s, file);<caret>
  }
}
