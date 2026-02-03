package example;

import example.ImportedClass.BAR;

import static example.ImportedClass.FOO;
import static example.ImportedClass.V;

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
