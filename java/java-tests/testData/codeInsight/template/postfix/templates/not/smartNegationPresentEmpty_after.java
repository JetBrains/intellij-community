import java.util.Optional;

public class Foo {
    void m() {
        if (Optional.of("").isEmpty()<caret>) {

        }
    }
}