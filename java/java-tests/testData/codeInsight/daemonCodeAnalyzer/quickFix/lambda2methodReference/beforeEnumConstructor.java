// "Replace lambda with method reference" "true"
import java.util.function.Consumer;

enum E {
    E(() -> {
        S<caret>ystem.out.println();
    });

    E(Runnable r) { }
}