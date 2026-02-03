/*
Value is always false (bar.equals("Asdasdd") && asdsa.length() == 12; line#32)
  One of the following happens:
    Operand #1 of and-chain is false (bar.equals("Asdasdd"); line#32)
      According to hard-coded contract, method 'equals' returns 'false' when bar != "Asdasdd" (equals; line#32)
        One of the following happens:
          'bar' was assigned (=; line#25)
            Values cannot be equal because "asdbar".length != "Asdasdd".length
              Left operand is 6 (foo + "bar"; line#25)
              and right operand is 7 ("Asdasdd"; line#32)
          or 'bar' was assigned (=; line#27)
            Values cannot be equal because "asdbaz".length != "Asdasdd".length
              Left operand is 6 (foo + "baz"; line#27)
              and right operand is 7 ("Asdasdd"; line#32)
    or operand #2 of and-chain is false (asdsa.length() == 12; line#32)
      Left operand is in {0..3} (asdsa.length(); line#32)
        Range is known from line #32 (bar.equals("Asdasdd"); line#32)
 */
class A {
  public A(String asdsa) {
    String foo = "asd";
    String bar;

    if (asdsa.length() > 4) {
      bar = foo + "bar";
    } else if (asdsa.length() >3) {
      bar = foo + "baz";
    } else {
      bar = "Asdasdd";
    }

    if (<selection>bar.equals("Asdasdd") && asdsa.length() == 12</selection>) {
      System.out.println("asd");
    }
  }
}