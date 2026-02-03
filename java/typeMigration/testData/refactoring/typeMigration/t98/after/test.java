import java.util.List;
public class Test {

    List<String> getArray(){
       return null;
    }

    void foo(String param) {
        List<String> array = getArray();
        for (int i = 0; i < array.size(); i++) {
           System.out.println(array.get(i));
           String str = array.get(i);
           param = array.get(i);
        }
    }

}
