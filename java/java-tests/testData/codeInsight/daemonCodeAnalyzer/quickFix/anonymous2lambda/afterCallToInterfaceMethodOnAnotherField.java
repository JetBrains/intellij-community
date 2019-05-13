// "Replace with lambda" "true"
class Test {
  Runnable a = () -> {};
  Runnable r = () -> a.run();
}