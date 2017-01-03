
import java.util.List;
import java.util.stream.Stream;

interface TreeUtils {

  static <K> Stream<List<K>> treeStream(List<K> treeItem) {
    Stream<List<K>> stream = null;
    stream.flatMap(TreeUtils::treeStream);
    return null;
  }
}