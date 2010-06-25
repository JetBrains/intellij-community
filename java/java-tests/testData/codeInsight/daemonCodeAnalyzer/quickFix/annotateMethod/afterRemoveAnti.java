// "Annotate overridden methods as @NotNull" "true"

import org.jetbrains.annotations.*;

 public class XEM {
     <caret>@NotNull
     String f(){
         return "";
     }
 }
 class XC extends XEM {
     @NotNull
     String f() {
         return "";
     }
 }
