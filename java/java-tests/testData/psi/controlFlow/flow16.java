// LocalsOrMyInstanceFieldsControlFlowPolicy
import java.io.IOException;

class TestIdea939 {
    public boolean test() throws IOException {<caret>
        try {
            return geta();
        } catch (IOException e) {
            throw new RuntimeException();
        } finally {
            geta();
        }
    }

    private boolean geta() throws IOException {
        return true;
    }

}

