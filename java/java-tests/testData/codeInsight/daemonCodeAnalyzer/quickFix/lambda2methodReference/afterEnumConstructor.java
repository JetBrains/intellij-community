// "Replace lambda with method reference" "true-preview"
import java.util.function.Consumer;

enum E {
    E(System.out::println);

    E(Runnable r) { }
}