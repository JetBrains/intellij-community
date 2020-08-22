import <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>;
import static <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>.f;
import static <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>.*;
import static <error descr="Class 'B' is in the default package">B</error>.g;

class Main {
  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>.f();
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">f</error>();
    <error descr="Text block literals are not supported at language level '9'">B</error>.g();
    g();
  }
}

@jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS)
class B {
  static void g() {}
}
