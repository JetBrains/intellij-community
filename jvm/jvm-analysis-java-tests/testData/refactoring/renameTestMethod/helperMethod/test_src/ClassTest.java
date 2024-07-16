import org.junit.jupiter.api.Test;

public class ClassTest {
    @Test
    public void testCheckSome() {
        assert false;
    }

    private int helperMeth<caret>od() {
        return 0;
    }
}