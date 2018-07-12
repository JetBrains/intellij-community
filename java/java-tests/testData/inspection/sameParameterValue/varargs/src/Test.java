public class Test {

   private void foo(String... args) {}

   public void bar() {
     foo("foo", "bar");
     foo("bla");
   }

   public static void main(String[] args){
     new Test().bar();
   }
}
class AnotherDiiferentVarargs {
    private static final String TEXT = "text";
    private static final String ANOTHER_TEXT = "another text";

    public static void main(String[] args) {
        printString(TEXT, "optional");
        printString(ANOTHER_TEXT);
    }

    private static void printString(String input, String... attrs) {
        System.out.println(input);
        for (String string : attrs) {
            System.out.println(string);
        }
    }
}