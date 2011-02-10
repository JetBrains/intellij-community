package p;

import static p.ToImportX.fff;
import static p.ToImportX2.fff;

//IDEA-64926
public class AppTest {
    public static void main(String[] args) {
        <ref>fff();
    }
}

class ToImportX {
    public static void fff() {
        System.out.println("ToImport");
    }
}

class ToImportX2 extends ToImportX {
    public static void fff() {
        System.out.println("ToImport2");
    }
}
