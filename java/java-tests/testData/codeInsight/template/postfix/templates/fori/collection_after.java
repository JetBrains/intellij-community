import java.util.ArrayList;

public class Foo {
    void m() {
        for (int i = 0; i < new ArrayList().size(); i++) {
            <caret>
        }
    }
}