import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;

class Main {
  static {
    <error descr="Patterns in 'instanceof' are not supported at language level '9'">requirePatternMatching</error>();
    <error descr="Text block literals are not supported at language level '9'">Main.requireTextBlocks</error>();
    <error descr="Records are not supported at language level '9'">new Main().requireRecords</error>();
  }

  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  static void requirePatternMatching(){}

  @PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS)
  static void requireTextBlocks(){}

  @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS)
  void requireRecords(){}

}
