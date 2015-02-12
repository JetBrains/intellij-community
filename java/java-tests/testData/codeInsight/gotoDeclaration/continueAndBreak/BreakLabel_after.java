class Test{
  {
  <caret>Label:
    while(true){
      foo();
      break Label;
    }
  }
}