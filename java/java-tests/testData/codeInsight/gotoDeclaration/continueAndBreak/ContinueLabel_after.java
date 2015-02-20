class Test{
  {
  <caret>Label:
    while(true){
      foo();
      continue Label;
    }
  }
}