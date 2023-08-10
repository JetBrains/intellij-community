// "Replace with lambda" "true-preview"
class Test {
  Runnable a = () -> {};
  Runnable r = () -> a.run();
}