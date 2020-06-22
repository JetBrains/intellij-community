import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;

class Main {
  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  Main(){}
  @PreviewFeature(feature=Feature.RECORDS)
  Main(long i){}
  @PreviewFeature(feature=Feature.TEXT_BLOCKS)
  Main(String s){}
  Main(int i){}

  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">new Main()</error>;
    <error descr="Records are not supported at language level '9'">new Main(42l)</error>;
    <error descr="Text block literals are not supported at language level '9'">new Main("42")</error>;
    new Main(42);
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">new org.myorg.preview.FromPreview() {
      public void g(){}
    }</error>;
  }

}
