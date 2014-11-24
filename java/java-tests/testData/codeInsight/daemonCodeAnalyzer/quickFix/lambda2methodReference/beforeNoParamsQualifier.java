// "Replace lambda with method reference" "false"
class Example {
  public void m() {
  }

  {
    Runnable r = () -> ex().<caret>m();
  }
  
  Example ex() {
    return this;
  }
}