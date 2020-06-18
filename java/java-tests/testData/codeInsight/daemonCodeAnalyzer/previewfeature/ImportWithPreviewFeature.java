<error descr="Patterns in 'instanceof' are not supported at language level '9'">import org.myorg.preview.FromPreview;</error>
<error descr="Patterns in 'instanceof' are not supported at language level '9'">import static org.myorg.preview.FromPreview.f;</error>

class Main {
  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>.f();
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">f()</error>;
  }
}
