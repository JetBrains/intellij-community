import java.util.List;
import java.util.function.BiConsumer;

class IWalker {
  public static void walk(I e) {
    e.getChildren().forEach(IWalker::walk);
    final BiConsumer<IWalker, I> walk = (iWalker, e1) -> IWalker.walk(e1);
  }

  interface I {
    List<I> getChildren();
  }
}