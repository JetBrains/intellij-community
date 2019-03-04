
class Test  {
 private static void p( Class<? extends Object> cl )
 {
    cl.<caret>getSuperclass();
 }
}