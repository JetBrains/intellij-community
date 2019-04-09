import java.util.List;

class Test
{
   <T> void foo(List<? extends List<T>> tr){
      <selection> tr.add(null);</selection>
   }

}