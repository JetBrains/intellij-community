import packageA.A;

import java.util.ArrayList;

import static packageA.A.staticImportedField;
import static packageA.A.staticImportedMethod;

public class B {
    public void foo() {
        Iterable<A> iterable = new ArrayList<A>();
        System.out.println(iterable.iterator().next().field);
        
        A a = new A();
        a.method();
        A.staticMethod();
        staticImportedMethod();
 
        System.out.println(a.field);
        System.out.println(A.staticField);
        System.out.println(staticImportedField);
        
        A b = new A() { };
    }
}