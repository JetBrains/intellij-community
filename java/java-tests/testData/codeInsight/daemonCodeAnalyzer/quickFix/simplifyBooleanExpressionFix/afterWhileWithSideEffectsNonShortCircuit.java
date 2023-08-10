// "Simplify 'pureConsumer(...)' to true extracting side effects" "true-preview"
import org.jetbrains.annotations.Contract;

public class Main {
    private static int counter = 0;

    public static void main(String[] args) {
        while (true) {
            sideEffect();
            if (!(counter < 5)) break;
            System.out.println(counter);
        }
    }

    @Contract(value = "!null->true;null->false", pure = true)
    private static boolean pureConsumer(Object consumed) {
        return consumed != null;
    }

    private static int sideEffect() {
        return counter++;
    }
}