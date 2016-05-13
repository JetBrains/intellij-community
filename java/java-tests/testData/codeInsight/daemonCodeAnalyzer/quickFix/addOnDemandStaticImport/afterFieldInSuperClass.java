// "Add static import for 'test.StaticImport.v'" "true"
package test;

import static test.StaticImport.v;

class StaticImport {
    static String v = "123";


}

class Child extends StaticImport {
    public static void main(String[] args) {
        System.out.println(v);

    }
}