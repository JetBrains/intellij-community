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
    new <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error>() {
      public void g(){}
    };
    new <error descr="Text block literals are not supported at language level '9'">NotDirectlyAnnotatedConstructor</error>();
    <error descr="Text block literals are not supported at language level '9'">new DirectlyAnnotatedConstructor()</error>;
  }

}

@PreviewFeature(feature=Feature.TEXT_BLOCKS)
class NotDirectlyAnnotatedConstructor { }

class DirectlyAnnotatedConstructor {
  @PreviewFeature(feature=Feature.TEXT_BLOCKS)
  DirectlyAnnotatedConstructor() {}
}
