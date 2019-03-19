import java.util.List;

public class Test
{
   <T> void foo(List<? extends List<T>> tr){
       tr.add(null);
   }//ins and outs
//in: PsiParameter:tr

}