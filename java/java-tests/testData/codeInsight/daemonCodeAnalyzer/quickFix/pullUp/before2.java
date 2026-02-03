// "Pull method 'foo' to 'List'" "false"
import java.util.List;

public abstract class Test implements List{
    @Overr<caret>ide
    void foo(){}
}
