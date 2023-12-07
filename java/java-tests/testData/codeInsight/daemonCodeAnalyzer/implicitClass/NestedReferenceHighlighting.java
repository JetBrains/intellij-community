String a = "!";

enum E {A, B}

record Rar() {
}

class AA {
  public void t(Rar rar) {

  }
}

void main() {
  System.out.println(a);
  Rar x = new Rar();
  new AA().t(x);
  System.out.println(x);
}