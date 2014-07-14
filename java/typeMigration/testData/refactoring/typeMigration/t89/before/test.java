import java.util.*;
public class Test {

    List<String> getArray(){
       return null;
    }

    void foo() {
        List<String> array = getArray();
        Collections.checkedList(array, String.class);
    }

}