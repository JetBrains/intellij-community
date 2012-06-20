import java.io.File;

class Zoo {
  private Zoo delegate;

  public void foo(String s, File file) {
  }
  public void foo(String s, File file, int a) {
    delegate.foo(s, file, a);<caret>
  }
}
