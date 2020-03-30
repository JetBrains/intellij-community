import java.util.List;

public class Test
{
   <T> void foo(List<? extends List<T>> tr){
       newMethod(tr);
   }

    private <T> void newMethod(List<? extends List<T>> tr) {
        tr.add(null);
    }

}