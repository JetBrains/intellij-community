import org.jetbrains.annotations.Nullable;

class Test {
    enum E { A, B }

    @Nullable
    static E getE() { return null; }

    public static void main(String[] args) {
        switch (<warning descr="Dereference of 'getE()' may produce 'NullPointerException'">getE()</warning>) {  // <<< should be highlighted as potential NPE
            case A:
            case B:
        }
    }
}