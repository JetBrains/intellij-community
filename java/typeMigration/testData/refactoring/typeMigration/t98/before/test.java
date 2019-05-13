import java.util.List;
public class Test {

    String[] getArray(){
       return null;
    }

    void foo(String param) {
        String[] array = getArray();
        for (int i = 0; i < array.length; i++) {
           System.out.println(array[i]);
           String str = array[i];
           param = array[i];
        }
    }

}
