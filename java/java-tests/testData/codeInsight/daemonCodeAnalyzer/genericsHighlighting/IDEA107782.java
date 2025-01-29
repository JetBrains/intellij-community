class Test {
  {
    final MyResult hello = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'MyResult'">parseXML</error>(new Parser());
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

