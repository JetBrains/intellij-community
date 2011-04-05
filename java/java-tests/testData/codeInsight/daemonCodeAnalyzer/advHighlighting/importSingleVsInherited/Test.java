import p.Base.Inner;
import p.BaseImpl;

class Test extends BaseImpl {
  void m() {
    Inner inner = new Inner() { };  // imported public Base.Inner should shadow inherited package-private BaseImpl.Inner
    BaseImpl.<error descr="'p.BaseImpl.Inner' is not public in 'p.BaseImpl'. Cannot be accessed from outside package">Inner</error> i2 = null;
  }
}
