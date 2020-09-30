import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;
import <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>;

class Custom1 implements <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error> {
  public void g() {}
}

class Custom2 implements <error descr="Patterns in 'instanceof' are not supported at language level '9'">FromPreview</error> {
  public void g() {}
}
