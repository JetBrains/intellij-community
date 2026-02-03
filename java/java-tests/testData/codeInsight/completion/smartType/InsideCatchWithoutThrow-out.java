class A{

{
 try{
  smth();
 }
 catch(ProcessCanceledException <caret>)
}
}

class ProcessCanceledException extends Throwable {}