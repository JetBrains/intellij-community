// access problems in inner classes

import x.BeanContextServicesSupport;
import x.BeanContextServicesSupport.<error descr="'x.BeanContextServicesSupport.BCSSChild' has protected access in 'x.BeanContextServicesSupport'">BCSSChild</error>;

class a extends x.Component {
    void f() {
        FlipBufferStrategy s = null;
        int i = s.<error descr="'numBuffers' has protected access in 'x.Component.FlipBufferStrategy'">numBuffers</error>;
        s.<error descr="'createBuffers(int)' has protected access in 'x.Component.FlipBufferStrategy'">createBuffers</error>(1);

        // TODO
        // now cannot distinquish private from package-private in class files
        //< error descr="'java.awt.Component.SingleBufferStrategy' has private access in 'java.awt.Component'">SingleBufferStrategy< /error> s2 = null;
        //Object o = s2.< error descr="'caps' has private access in 'java.awt.Component.SingleBufferStrategy'">caps< /error>;
    }



        class ddd extends BeanContextServicesSupport {
            BCSSChild.<error descr="'x.BeanContextServicesSupport.BCSSChild.BCSSCServiceClassRef' is not public in 'x.BeanContextServicesSupport.BCSSChild'. Cannot be accessed from outside package">BCSSCServiceClassRef</error> fd = null;
            void ff() {
                fd.<error descr="'addRequestor(java.lang.Object)' is not public in 'x.BeanContextServicesSupport.BCSSChild.BCSSCServiceClassRef'. Cannot be accessed from outside package">addRequestor</error>(null,null);
            }
        }

}


interface I {
  abstract class Imple implements I {
    abstract void f();
  }
  abstract class Impl2 extends Imple {
    abstract class Inner extends Impl2 {

    }
  }
}


class Class1 extends Class2 {
  public void test() {
    new <error descr="'Class2.Class2Inner' has private access in 'Class2'">Class2Inner</error>();
    int i = <error descr="'Class2.Class2Inner' has private access in 'Class2'">Class2Inner</error>.i;
  }
}
class Class2 {
  private static class Class2Inner{
    public static int i;
    public Class2Inner() {
    }
  }
}
class Ax {
    public interface B {}
}
