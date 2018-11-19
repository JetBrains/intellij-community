class DoubleTrouble {
    public static void main(String[] args) {
        {
        Double a = Double.NaN;
        double b = a;//Double.NaN;
        if (<warning descr="Condition 'b == Double.NaN' is always 'false'">b == Double.NaN</warning>) {
            ;
        }
        }

        if (<warning descr="Condition 'Float.NaN != Float.NaN' is always 'true'">Float.NaN != Float.NaN</warning>) {

        }

        {
          double a = Double.NaN;
          double b = Double.NaN;
          if (<warning descr="Condition 'Double.NaN == a' is always 'false'">Double.NaN == a</warning>) {}
          if (<warning descr="Condition 'b == a' is always 'false'">b == a</warning>) {}
        }
    }
}
