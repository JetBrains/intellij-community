import java.util.List;

class Some {

    public static void main(String[] args) {
        boolean x = true, y = true, z = true, t = true;
        boolean r = <warning descr="Condition 'x ^ y ^ z ^ t' is always 'false'"><warning descr="Condition 'x ^ y ^ z' is always 'true'"><warning descr="Condition 'x ^ y' is always 'false'">x ^ y</warning> ^ z</warning> ^ t</warning>;
        System.out.println("r: " + r);
    }

}
