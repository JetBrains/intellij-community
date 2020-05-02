import java.util.List;

public class Test
{
   <T> void foo(List<? extends List<T>> tr){
      <selection> tr.add(null);</selection>
   }

}