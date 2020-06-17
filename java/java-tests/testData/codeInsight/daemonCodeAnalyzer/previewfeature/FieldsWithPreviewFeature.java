import jdk.internal.PreviewFeature;
import jdk.internal.PreviewFeature.Feature;

class Main {
  @PreviewFeature(feature=Feature.PATTERN_MATCHING_IN_INSTANCEOF)
  static String instanceOf;
  @PreviewFeature(feature=Feature.RECORDS)
  static long records;
  @PreviewFeature(feature=Feature.TEXT_BLOCKS)
  String textBlocks;
  int i;
  <error descr="Patterns in 'instanceof' are not supported at language level '8'">org.myorg.preview.FromPreview</error> preview;

  static {
    String s = <error descr="Text block literals are not supported at language level '8'">new Main().textBlocks</error>;
    String o = <error descr="Patterns in 'instanceof' are not supported at language level '8'">Main.instanceOf</error>;
    long l = <error descr="Records are not supported at language level '8'">records</error>;
    int k = new Main().i;
    <error descr="Patterns in 'instanceof' are not supported at language level '8'">org.myorg.preview.FromPreview</error> local = null;
  }

}
