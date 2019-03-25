import java.util.List;

public class Test
{
   <T> void foo(List<? extends List<T>> tr){
       tr.add(null);
   }//ins and outs
//in: PsiParameter:tr
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod(List<? extends List<T>> tr) {
        tr.add(null);
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }

}