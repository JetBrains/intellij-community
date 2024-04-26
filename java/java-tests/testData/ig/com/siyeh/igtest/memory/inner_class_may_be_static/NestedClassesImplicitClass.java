public static class Nested {
  public class <warning descr="Inner class 'Nested2' may be 'static'">Nes<caret>ted2</warning> {
  }
}


public void main(String[] args) {
  Nested nested = new Nested();
}

public void t(Nested.Nested2 nested2){

}

