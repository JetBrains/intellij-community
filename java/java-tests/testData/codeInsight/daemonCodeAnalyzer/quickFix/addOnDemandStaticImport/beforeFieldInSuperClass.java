// "Add static import for 'test.StaticImport.v'" "true-preview"
package test;

class StaticImport {
    static String v = "123";


}

class Child extends StaticImport {
    public static void main(String[] args) {
        System.out.println(StaticImport.<caret>v);

    }
}