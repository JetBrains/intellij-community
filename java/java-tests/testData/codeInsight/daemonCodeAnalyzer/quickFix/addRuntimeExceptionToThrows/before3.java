// "Add 'throws RuntimeException' to method signature" "false"
class a {
   int f() {
     Runnable r = () -> {
       throw new RuntimeException()<caret>;
     }
   }
}

