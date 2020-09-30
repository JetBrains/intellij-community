import <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>;

class Main {
  static {
    Runnable r = <error descr="Patterns in 'instanceof' are not supported at language level '9'">FromPreview</error>::f;
  }
  void f(<error descr="Patterns in 'instanceof' are not supported at language level '9'">FromPreview</error> fp) {
    Runnable r = <error descr="Patterns in 'instanceof' are not supported at language level '9'">fp::g</error>;
  }
}