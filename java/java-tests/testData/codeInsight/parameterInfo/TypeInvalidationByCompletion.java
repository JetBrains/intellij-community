import java.util.ArrayList;

class A{
    void foo(){
        new ArrayList<String>().stream().collect(toSe<caret>)
    }
}
