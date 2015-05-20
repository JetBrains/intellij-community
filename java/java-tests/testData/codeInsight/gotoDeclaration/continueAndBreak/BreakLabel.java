class Test{
  {
  Label:
    while(true){
      foo();
      break <caret>Label;
    }
  }
}