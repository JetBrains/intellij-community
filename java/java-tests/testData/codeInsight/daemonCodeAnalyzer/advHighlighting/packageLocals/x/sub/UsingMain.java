package x.sub;

import x.ContainerClass;
import x.A;

public class UsingMain {
    public static void main(String[] args) {
        final ContainerClass cont = new ContainerClass();
        System.out.println(cont.cc.<error descr="'x.ComponentClass' is not public in 'x'. Cannot be accessed from outside package">size</error>);////
        new A().foo();
    }
}
