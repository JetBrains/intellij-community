// "Add runtime exception(s) to method signature" "false"
class a {
   int f() {
     Runnable r = () -> {
       throw new RuntimeException()<caret>;
     }
   }
}

