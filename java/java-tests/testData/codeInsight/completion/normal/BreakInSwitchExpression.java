public class ConstConfig {
    int test(int x) {
        return switch (x) {
            case 1:
                if (Math.random() > 0.5) br<caret>
        };
    }
}