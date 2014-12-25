// "Replace with expression lambda" "true"
class Test {
  {
    Runnable r = () -> <caret>{
      System.out.println(""//todo comment
      );
    };
  }
}