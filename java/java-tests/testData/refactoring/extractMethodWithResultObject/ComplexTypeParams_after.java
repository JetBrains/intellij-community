import java.util.List;

class Test
{
   <T> void foo(List<? extends List<T>> tr){
       NewMethodResult x = newMethod(tr);
   }

    <T> NewMethodResult newMethod(List<? extends List<T>> tr) {
        tr.add(null);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

}