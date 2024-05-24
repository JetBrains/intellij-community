import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Integer code) {
        <caret>return switch (code) {
            case 100 -> "Continue";
            case 200 -> "OK";
            case 301 -> "Moved permanently";
            case Integer i when code > 502 && i < 600 -> "Server error";
            default -> "unknown code";
        };
    }
}