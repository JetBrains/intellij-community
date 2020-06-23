import org.jetbrains.annotations.Nullable;

class Test {
   String foo(int i, boolean flag) {

       String xxx = newMethod(i, flag);
       if (xxx == null) return null;

       System.out.println(xxx);
      return null;
   }

    @Nullable
    private String newMethod(int i, boolean flag) {
        String xxx = "";
        if (flag) {
            for (int j = 0; j < 100; j++) {
                if (i == j) {
                    return null;
                }
            }
        }
        return xxx;
    }
}