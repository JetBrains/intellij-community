import org.jetbrains.annotations.Nullable;

public class Test {
    enum E { A, B }

    @Nullable
    static E getE() { return null; }

    public static void main(String[] args) {
        switch (getE()) {  // <<< should be highlighted as potential NPE
            case A:
            case B:
        }
    }
}