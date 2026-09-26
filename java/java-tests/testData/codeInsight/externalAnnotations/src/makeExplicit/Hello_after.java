package makeExplicit;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class Hello {
    @NotNull
    @Contract(pure = true)
    private String t<caret>est(List<String> list) {
        return null;
    }
}