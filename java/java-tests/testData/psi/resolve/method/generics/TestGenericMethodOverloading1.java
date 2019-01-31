
import java.util.List;

public class TestGenericMethodOverloading1 {
    class A<T extends List>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A().<caret>equals(new Object());
    }
}
