// "Replace with expression lambda" "true-preview"
class Test {
  {
    Runnable r = () -> <caret>{
      System.out.println(""//todo comment
      );
    };
  }
}