import java.util.*;

class AAA {
    private void foo(List<String> list) {
        for (String val : list) {
            <caret>checkVal(val);
        }
    }

    private void checkVal(String message) {
        if (message == null) return;
        message = message.trim();
        if (message.isEmpty()) return;
        try {
            Integer.parseInt(message);
        } catch (NumberFormatException e) {
            return;
        }
        throw new IllegalArgumentException("Should not be a number!");
    }
}