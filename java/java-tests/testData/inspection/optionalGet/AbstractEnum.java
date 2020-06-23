import java.util.Optional;

class AAA {
  <error descr="Modifier 'abstract' not allowed here">abstract</error> enum X {A, B;}

  public static void main(String[] args) {
    Optional.of(args.length > 0 ? X.A : X.B).get();
  }
}