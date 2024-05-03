import org.junit.jupiter.api.Test;

public class ClassTest {
    @Test
    public void testCheck<caret>Some() {
        assert false;
    }

    private int helperMethod() {
        return 0;
    }
}