import jdk.internal.javac.PreviewFeature;
import jdk.internal.javac.PreviewFeature.Feature;
import <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.jdk.internal.javac.preview.FromPreview</error>;

class Custom1 implements <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.jdk.internal.javac.preview.FromPreview</error> {
  public void g() {}
}

class Custom2 implements <error descr="Patterns in 'instanceof' are not supported at language level '9'">FromPreview</error> {
  public void g() {}
}
