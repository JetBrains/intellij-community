class Test {
  {
    final MyResult hello = parseXML(new Parser());
  }
  public <R, P extends AbstractParser & Result<R>> R parseXML(P parser) {
    R result = null;
    return result;
  }
}
class MyResult {}

class AbstractParser {}
interface Result<T> {}
class Parser extends AbstractParser implements Result {}

