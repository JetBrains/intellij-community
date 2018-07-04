import java.util.ArrayList;
import java.util.stream.Collectors;

class A{
    void foo(){
        new ArrayList<String>().stream().collect(Collectors.toSet())<caret>
    }
}
