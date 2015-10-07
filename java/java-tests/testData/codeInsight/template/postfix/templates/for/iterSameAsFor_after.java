import java.util.ArrayList;

class Test {
    public static void main(String[] args) {
        ArrayList<Object> list = new ArrayList<Object>();
        for (Object o : list) {
            <caret>      
        }
    }
}