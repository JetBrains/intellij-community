import org.jetbrains.annotations.*;

class Test {
     @Nullable private final String baseFile;
     @Nullable private final String baseFile1;


     public Test(@NotNull String baseFile) {
         this.baseFile = baseFile;
         this.baseFile1 = null;
     }

     public Test(@NotNull String baseFile1, boolean a) {
         this.baseFile1 = baseFile1;
         if (baseFile1.contains("foo")) {
           this.baseFile = null;
         } else {
           this.baseFile = null;
         }
     }
}