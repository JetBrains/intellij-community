import java.util.List;

class Test
{
   <T> void foo(List<? extends List<T>> tr){
       tr.add(null);
   }//ins and outs
//in: PsiParameter:tr
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod(List<? extends List<T>> tr) {
        tr.add(null);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }

}