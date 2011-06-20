// access problems in inner classes

import java.awt.*;
import java.beans.beancontext.BeanContextServicesSupport;
import java.beans.beancontext.BeanContextServicesSupport.<error descr="'java.beans.beancontext.BeanContextServicesSupport.BCSSChild' has protected access in 'java.beans.beancontext.BeanContextServicesSupport'">BCSSChild</error>;

class a extends Component {
    void f() {
        FlipBufferStrategy s = null;
        int i = s.<error descr="'numBuffers' has protected access in 'java.awt.Component.FlipBufferStrategy'">numBuffers</error>;
        s.<error descr="'createBuffers(int, java.awt.BufferCapabilities)' has protected access in 'java.awt.Component.FlipBufferStrategy'">createBuffers</error>(1,null);

        // TODO
        // now cannot distinquish private from package local in class files
        //< error descr="'java.awt.Component.SingleBufferStrategy' has private access in 'java.awt.Component'">SingleBufferStrategy< /error> s2 = null;
        //Object o = s2.< error descr="'caps' has private access in 'java.awt.Component.SingleBufferStrategy'">caps< /error>;
    }



        class ddd extends BeanContextServicesSupport {
            BCSSChild.<error descr="'java.beans.beancontext.BeanContextServicesSupport.BCSSChild.BCSSCServiceClassRef' is not public in 'java.beans.beancontext.BeanContextServicesSupport.BCSSChild'. Cannot be accessed from outside package">BCSSCServiceClassRef</error> fd = null;
            void ff() {
                fd.<error descr="'addRequestor(java.lang.Object, java.beans.beancontext.BeanContextServiceRevokedListener)' is not public in 'java.beans.beancontext.BeanContextServicesSupport.BCSSChild.BCSSCServiceClassRef'. Cannot be accessed from outside package">addRequestor</error>(null,null);
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
