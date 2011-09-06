import java.util.ArrayList;
public abstract class Test extends ArrayList<String> implements Int {
    @Overr<caret>ide
    void foo(){}
}

interface Int {}
