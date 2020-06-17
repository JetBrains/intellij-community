import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;

class Main {
  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  class InstanceOf{}
  @PreviewFeature(feature=Feature.RECORDS)
  class Records{}
  @PreviewFeature(feature=Feature.TEXT_BLOCKS)
  class TextBlocks{}
  class Empty{}

  private void f(<error descr="Patterns in 'instanceof' are not supported at language level '8'">InstanceOf</error> o, Empty e) {}
  private void f(Empty e, <error descr="Records are not supported at language level '8'">Records</error> r) {}
  private void f(<error descr="Text block literals are not supported at language level '8'">TextBlocks</error> e, <error descr="Records are not supported at language level '8'">Records</error> r) {}
  private void f(<error descr="Patterns in 'instanceof' are not supported at language level '8'">org.myorg.preview.FromPreview</error> p) {}
}
