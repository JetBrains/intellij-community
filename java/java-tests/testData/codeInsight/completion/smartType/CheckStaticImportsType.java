import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.*;

public class TestClass {
    private List<String> testList;

    public void testMe() {
        List<String> newList = new ArrayList<String>(at<caret>);
    }
}