package example;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import example.ImportedClass.I1;
import example.ImportedClass.I1A;
import static example.ImportedClass.BAZZ.E1;
import static example.ImportedClass.V;
import static example.ImportedClass.FOO;
import example.ImportedClass.BAR;

public class MyTest {
    protected MyTest() {
        super();
        String az = BAR.AZ + V;
        System.out.println(FOO);
    }
}

class ImportedClass {
    public static String V = "";

    public static enum I1 {
        E1;
    }
    public static enum I1A {
        E1;
    }

    public static enum BAR {
        E1, AZ;
    }

    public static String FOO = "";

    public static enum BAZZ {
        E1;
    }


}
