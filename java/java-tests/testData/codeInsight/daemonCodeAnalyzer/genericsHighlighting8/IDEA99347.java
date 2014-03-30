interface Errors {
  java.util.Collection<java.lang.String> getErrorMessages();

}

class Test extends AbstrClass implements Errors {
}


abstract class AbstrClass {
  public java.util.Collection getErrorMessages() {return null;}
}
