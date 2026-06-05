package makeExplicitParam;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

class Hello {
    private String test(@Unmodifiable @NotNull List<String> l<caret>ist) {
        return null;
    }
}