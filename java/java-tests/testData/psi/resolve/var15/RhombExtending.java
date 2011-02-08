/** @noinspection UnusedDeclaration*/
interface I<T> {
    String SSS = "SSS";
}

interface II<T> extends I<T> {}

class A implements I {}

/** @noinspection UnusedDeclaration*/
class AA extends A implements II {
    public static void f() {
        String s = <ref>SSS;  //this is not ambigous reference
    }
}
