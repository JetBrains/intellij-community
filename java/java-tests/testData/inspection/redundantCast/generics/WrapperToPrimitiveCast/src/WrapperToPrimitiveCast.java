public class Main {
    public static void main(String[] args) {
        Integer J = 4;
        Integer I = new Integer(4);

        System.out.println(I == J);
        System.out.println((int) I == J);
        int j = (int)J;
        System.out.println((int) I == j);

        int p = 555555;
        Integer W = (Integer) p;
        System.out.println((Integer) p == W);
        int w = W;
        System.out.println((Integer) p == w);

        Integer test = 10;
        double d = ((double)test/100);

        Double number = Double.valueOf(3);
        long integerPart = (long) (double) number;

        Long lnumber = Long.valueOf(3);
        long integerPartL = (long) (long) lnumber;
    }
}

class Foo<T> {
  T foo() {
    return (T)(Object)1;
  }
}
