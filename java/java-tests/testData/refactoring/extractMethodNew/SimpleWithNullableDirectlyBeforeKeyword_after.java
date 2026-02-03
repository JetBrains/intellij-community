import org.jetbrains.annotations.Nullable;

public class Test {
    public void test(){
      String a;
        a = newMethod();
        return a;
    }

    @Nullable
    private String newMethod() {
        String a;
        if (1 == 0) {
         a = "1";
       } else {
         a = null;
       }
        return a;
    }
}