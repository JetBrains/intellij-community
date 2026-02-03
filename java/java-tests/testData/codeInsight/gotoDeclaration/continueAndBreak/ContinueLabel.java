class Test{
  {
  Label:
    while(true){
      foo();
      continue <caret>Label;
    }
  }
}