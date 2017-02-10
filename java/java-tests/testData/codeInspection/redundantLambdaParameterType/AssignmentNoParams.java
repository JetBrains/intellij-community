// "Remove redundant types" "false"
class Test {
  {
    Runnable r = (<caret>) -> {};
  }
}