import java.util.*;
public class Test {

    List<String> getArray(){
       return null;
    }

    void foo() {
        List<String> array = getArray();
        Collections.binarySearch(array, "");
        Collections.sort(array);
    }

}