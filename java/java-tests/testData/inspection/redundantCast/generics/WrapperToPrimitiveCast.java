class Main {
    public static void main(String[] args) {
        Integer J = 4;
        Integer I = new Integer(4);

        System.out.println(I == J);
        System.out.println((int) I == J);
        int j = (<warning descr="Casting 'J' to 'int' is redundant">int</warning>)J;
        System.out.println((<warning descr="Casting 'I' to 'int' is redundant">int</warning>) I == j);

        int p = 555555;
        Integer W = (<warning descr="Casting 'p' to 'Integer' is redundant">Integer</warning>) p;
        System.out.println((Integer) p == W);
        int w = W;
        System.out.println((<warning descr="Casting 'p' to 'Integer' is redundant">Integer</warning>) p == w);

        Integer test = 10;
        double d = ((double)test/100);

        Double number = Double.valueOf(3);
        long integerPart = (long) (double) number;

        Long lnumber = Long.valueOf(3);
        long integerPartL = (<warning descr="Casting '(long)lnumber' to 'long' is redundant">long</warning>) (<warning descr="Casting 'lnumber' to 'long' is redundant">long</warning>) lnumber;
    }
}

class Foo<T> {
  T foo() {
    return (T)(Object)1;
  }
}
