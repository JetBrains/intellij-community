import java.util.List;
import java.util.Map;

abstract class NoImports implements List<NoImports.A> {
  Map.Entry entry;
  A a;

  class A {}
}
class RecordQualifier {
  private static record CalcResult(Type type) {

    enum Type {
      OK, ERROR
    }
  }
}