import java.util.List;
import java.util.function.BiConsumer;

class IWalker {
  public void w<caret>alk(I e) {
    e.getChildren().forEach(this::walk);
    final BiConsumer<IWalker, I> walk = IWalker::walk;
  }

  interface I {
    List<I> getChildren();
  }
}