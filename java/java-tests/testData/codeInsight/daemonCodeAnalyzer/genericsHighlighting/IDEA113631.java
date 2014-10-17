interface BP<T extends BPCT<? extends D>> { }
abstract class AS<T extends SC> extends TPBP<T> { }
class BPCT<T extends D> extends CT<D> { }
class S extends AS { }
class CT<T extends D> { }
class TPBP<T extends BPCT<? extends D>> implements BP<T> { }
class SPD extends D { }
class SC extends BPCT<SPD> { }
class D { }

class XTest {
  public Class<? extends BP<? extends BPCT<SPD>>> getBpClass() {
    return (Class<? extends BP<? extends BPCT<SPD>>>) getSClass();
  }

  protected Class<? extends AS> getSClass() { return S.class; }

  public static void main(String[] args) {
    System.out.println(new XTest().getBpClass());
  }

}